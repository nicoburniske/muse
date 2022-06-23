package muse.service

import muse.domain.session.UserSession
import muse.domain.spotify.InitialAuthData
import muse.utils.Utils
import zio.*

import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit

// TODO: make generic?
trait UserSessions {
  def addUserSession(userId: String, authData: InitialAuthData): UIO[String]
  def getUserSession(sessionId: String): UIO[Option[UserSession]]
  def deleteUserSession(sessionId: String): UIO[Unit]
  def deleteUserSessionByUserId(userId: String): UIO[Unit]
  def updateUserSession(sessionId: String)(f: UserSession => UserSession): UIO[Option[UserSession]]

  // TODO: should there be persistence?
  def loadSessions: UIO[Unit]

  final def getAccessToken(sessionId: String)  = getUserSession(sessionId).map(_.map(_.accessToken))
  final def getUserId(sessionId: String)       = getUserSession(sessionId).map(_.map(_.id))
  final def getRefreshToken(sessionId: String) = getUserSession(sessionId).map(_.map(_.refreshToken))
}

object UserSessions {
  val live = ZLayer(Ref.Synchronized.make(Map.empty).map(UserSessionsLive.apply(_)))

  def addUserSession(userId: String, authData: InitialAuthData) =
    ZIO.serviceWithZIO[UserSessions](_.addUserSession(userId, authData))

  def getUserSession(sessionId: String) = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))

  def updateUserSession(sessionId: String)(f: UserSession => UserSession) =
    ZIO.serviceWithZIO[UserSessions](_.updateUserSession(sessionId)(f))

  def deleteUserSession(sessionId: String) = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))

  def deleteUserSessionByUserId(userId: String) =
    ZIO.serviceWithZIO[UserSessions](_.deleteUserSessionByUserId(userId))
}

// TODO: should this ref be synchronized?
final case class UserSessionsLive(sessionsR: Ref.Synchronized[Map[String, UserSession]])
    extends UserSessions {

  // TODO: confirm this works for multiple sessions.
  override final def addUserSession(userId: String, authData: InitialAuthData) = for {
    expiration <- Utils.getExpirationInstant(authData.expiresIn)
    guid       <- Random.nextUUID
    newSession  = guid.toString
    session     = UserSession(newSession, userId, expiration, authData.accessToken, authData.refreshToken)
    _          <- sessionsR.update(_ + (newSession -> session))
  } yield newSession

  override final def getUserSession(sessionId: String) = sessionsR.get.map(_.get(sessionId))

  override final def deleteUserSession(sessionId: String) = sessionsR.update(_.removed(sessionId))

  override final def deleteUserSessionByUserId(userId: String) =
    sessionsR.update(_.filterNot(_._2.id == userId))

  override final def updateUserSession(sessionId: String)(f: UserSession => UserSession) = sessionsR
    .updateAndGet { sessions =>
      sessions.get(sessionId).fold(sessions) { current => sessions.updated(sessionId, f(current)) }
    }
    .map(_.get(sessionId))

  def loadSessions: UIO[Unit] = ???
}
