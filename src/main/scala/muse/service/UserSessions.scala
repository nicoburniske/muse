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
  def getUserBulkhead(sessionId: String): UIO[Bulkhead]
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
      // This is a cache just to make sure we don't grow forever?
      bulkheadCache   <- Cache.make(
                           10000,
                           1.hour,
                           Lookup((_: String) => Bulkhead.make(maxInFlightCalls = 5, maxQueueing = 5))
                         )
      databaseService <- ZIO.service[DatabaseService]
      _               <- startCacheReporter(cache)
    } yield UserSessionsLive(cache, bulkheadCache, databaseService)
  }

  def getUserSession(sessionId: String)     = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def getUserBulkhead(sessionId: String)    = ZIO.serviceWithZIO[UserSessions](_.getUserBulkhead(sessionId))
  def deleteUserSession(sessionId: String)  = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))
  def refreshUserSession(sessionId: String) = ZIO.serviceWithZIO[UserSessions](_.refreshUserSession(sessionId))

  private def getUserSessionLive(sessionId: String) = for {
    maybeSession <- DatabaseService.getUserSession(sessionId)
    session      <- ZIO.fromOption(maybeSession).orElseFail(Unauthorized(s"Invalid User Session."))
    authInfo     <- SpotifyAuthService
                      .requestNewAccessToken(session.refreshToken)
                      .retry(retrySchedule)
  } yield UserSession(sessionId, session.userId, authInfo.accessToken)

  def startCacheReporter(cache: Cache[String, Throwable, UserSession]) = {
    val reporter = CacheUtils.ZioCacheStatReporter("user_sessions", cache)
    reporter.report.repeat(Schedule.spaced(10.seconds) && Schedule.forever).forkDaemon
  }

  val retrySchedule = Schedule.recurs(2) && Schedule.exponential(50.millis) && Schedule.recurWhile {
    case SpotifyAuthError(status, _) if status.isServerError => true
    case _                                                   => false
  }
}

final case class UserSessionsLive(
    userCache: Cache[String, Throwable, UserSession],
    bulkheadCache: Cache[String, Nothing, Bulkhead],
    databaseService: DatabaseService)
    extends UserSessions {

  override def getUserSession(sessionId: String)     = getSession(sessionId)
  override def getUserBulkhead(sessionId: String)    = bulkheadCache.get(sessionId)
  override def refreshUserSession(sessionId: String) = userCache.invalidate(sessionId) *> getSession(sessionId)

  private def getSession(sessionId: String) = userCache.get(sessionId).flatMapError {
    // After we get a revocation error delete the session from DB.
    case SpotifyAuthError(_, SpotifyAuthErrorResponse.revoked) =>
      deleteUserSession(sessionId).ignore *> ZIO.succeed(Unauthorized("User Session Revoked."))
    case e                                                     => ZIO.succeed(e)
  }

  override def deleteUserSession(sessionId: String) =
    bulkheadCache.invalidate(sessionId) *>
      userCache.invalidate(sessionId) *>
      databaseService.deleteUserSession(sessionId)
}
