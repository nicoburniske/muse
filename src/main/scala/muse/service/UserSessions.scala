package muse.service

import muse.domain.tables.AppUser
import muse.persist.DatabaseQueries
import zio.*

import java.sql.SQLException

trait UserSessions {
  def addUserSession(user: AppUser): UIO[String]
  def getUserSession(sessionId: String): UIO[Option[AppUser]]
  def deleteUserSession(sessionId: String): UIO[Unit]
  def deleteUserSessionByUserId(userId: String): UIO[Unit]

  // TODO: should there be persistence?
  def loadSessions: UIO[Unit]

  final def getAccessToken(sessionId: String)  = getUserSession(sessionId).map(_.map(_.accessToken))
  final def getUserId(sessionId: String)       = getUserSession(sessionId).map(_.map(_.id))
  final def getRefreshToken(sessionId: String) = getUserSession(sessionId).map(_.map(_.refreshToken))
}

object UserSessions {
  val live = ZLayer(Ref.make(Map.empty).map(UserSessionsLive.apply(_)))

  def addUserSession(user: AppUser)             = ZIO.serviceWithZIO[UserSessions](_.addUserSession(user))
  def getUserSession(sessionId: String)         = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def deleteUserSession(sessionId: String)      = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))
  def deleteUserSessionByUserId(userId: String) =
    ZIO.serviceWithZIO[UserSessions](_.deleteUserSessionByUserId(userId))
}

final case class UserSessionsLive(sessionsR: Ref[Map[String, AppUser]]) extends UserSessions {
  val SESSION_LENGTH = 30

  override final def addUserSession(user: AppUser) = for {
    _          <- deleteUserSessionByUserId(user.id)
    newSession <- Random.nextString(SESSION_LENGTH)
    _          <- sessionsR.update(_ + (newSession -> user))
  } yield newSession

  override final def getUserSession(sessionId: String)         = sessionsR.get.map(_.get(sessionId))
  override final def deleteUserSession(sessionId: String)      = sessionsR.update(_.removed(sessionId))
  override final def deleteUserSessionByUserId(userId: String) =
    sessionsR.update(_.filterNot(_._2.id == userId))

  def loadSessions: UIO[Unit] = ???
}
