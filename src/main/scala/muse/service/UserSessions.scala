package muse.service

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.AuthCodeFlowData
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zio.*
import zio.cache.{Cache, Lookup}
import zio.json.*
import zio.stream.{ZPipeline, ZStream}

import java.io.IOException
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit

trait UserSessions {
  def getUserSession(sessionId: String): IO[Throwable, UserSession]
  def deleteUserSession(sessionId: String): UIO[Unit]
}

object UserSessions {
  val layer = ZLayer.fromZIO {
    for {
      cache <- Cache.make(
                 1000,
                 // Default TTL is 1 hour.
                 59.minutes,
                 Lookup(getUserSessionLive)
               )
    } yield UserSessionsLive(cache)
  }

  def getUserSession(sessionId: String)    = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def deleteUserSession(sessionId: String) = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))

  private def getUserSessionLive(sessionId: String) = for {
    maybeSession <- DatabaseService.getUserSession(sessionId)
    session      <- ZIO.fromOption(maybeSession).orElseFail(Unauthorized(s"Invalid User Session."))
    authInfo     <- SpotifyAuthService.requestNewAccessToken(session.refreshToken).retry(Schedule.recurs(2))
    spotify      <- SpotifyService.live(authInfo.accessToken)
    instant      <- Clock.instant
    expiration    = instant.plus(authInfo.expiresIn, ChronoUnit.SECONDS).minus(1, ChronoUnit.MINUTES)
  } yield UserSession(sessionId, session.userId, expiration, authInfo.accessToken, session.refreshToken, spotify)
}

final case class UserSessionsLive(cache: Cache[String, Throwable, UserSession]) extends UserSessions {
  override def getUserSession(sessionId: String)    = cache.get(sessionId)
  override def deleteUserSession(sessionId: String) = cache.invalidate(sessionId)
}
