package muse.service

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.auth.{AuthCodeFlowData, SpotifyAuthErrorResponse, SpotifyAuthError}
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.utils.Utils
import nl.vroste.rezilience.Bulkhead
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
  def deleteUserSession(sessionId: String): IO[Throwable, Boolean]
  def refreshUserSession(sessionId: String): IO[Throwable, UserSession]
}

object UserSessions {
  val layer = ZLayer.fromZIO {
    for {
      cache           <- Cache.make(
                           1000,
                           // Default TTL is 1 hour.
                           59.minutes,
                           Lookup(getUserSessionLive)
                         )
      databaseService <- ZIO.service[DatabaseService]
      _               <- startCacheReporter(cache)
    } yield UserSessionsLive(cache, databaseService)
  }

  def getUserSession(sessionId: String)     = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def deleteUserSession(sessionId: String)  = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))
  def refreshUserSession(sessionId: String) = ZIO.serviceWithZIO[UserSessions](_.refreshUserSession(sessionId))

  private def getUserSessionLive(sessionId: String) = for {
    maybeSession <- DatabaseService.getUserSession(sessionId)
    session      <- ZIO.fromOption(maybeSession).orElseFail(Unauthorized(s"Invalid User Session."))
    authInfo     <- SpotifyAuthService
                      .requestNewAccessToken(session.refreshToken)
                      .retry(retrySchedule)
    spotify      <- SpotifyService.live(authInfo.accessToken)
    bulkhead     <- Bulkhead.make(maxInFlightCalls = 5, maxQueueing = 0)
  } yield UserSession(sessionId, session.userId, authInfo.accessToken, spotify, bulkhead)

  def startCacheReporter(cache: Cache[String, Throwable, UserSession]) = {
    val reporter = CacheUtils.ZioCacheStatReporter("user_sessions", cache)
    reporter.report.repeat(Schedule.spaced(10.seconds) && Schedule.forever).forkDaemon
  }

  val retrySchedule = Schedule.recurs(2) && Schedule.exponential(50.millis) && Schedule.recurWhile {
    case SpotifyAuthError(status, _) if status.isServerError => true
    case _                                                   => false
  }
}

final case class UserSessionsLive(cache: Cache[String, Throwable, UserSession], databaseService: DatabaseService)
    extends UserSessions {
  override def getUserSession(sessionId: String)     = getSession(sessionId)
  override def refreshUserSession(sessionId: String) = cache.invalidate(sessionId) *> getSession(sessionId)

  private def getSession(sessionId: String) = cache.get(sessionId).flatMapError {
    // After we get a revocation error delete the session from DB.
    case SpotifyAuthError(_, SpotifyAuthErrorResponse.revoked) =>
      deleteUserSession(sessionId).ignore *> ZIO.succeed(Unauthorized("User Session Revoked."))
    case e                                                     => ZIO.succeed(e)
  }

  override def deleteUserSession(sessionId: String) = cache.invalidate(sessionId) *> databaseService.deleteUserSession(sessionId)
}
