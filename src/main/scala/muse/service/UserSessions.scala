package muse.service

import muse.domain.common.Types.{AccessToken, SessionId, UserId}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.auth.{AuthCodeFlowData, SpotifyAuthError, SpotifyAuthErrorResponse}
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
  def getUserSession(sessionId: SessionId): IO[Throwable, UserSession]
  def getUserBulkhead(userId: UserId): UIO[Bulkhead]
  def deleteUserSession(sessionId: SessionId): IO[Throwable, Boolean]
  def refreshUserSession(sessionId: SessionId): IO[Throwable, UserSession]
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

  def getUserSession(sessionId: SessionId)     = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def getUserBulkhead(userId: UserId)          = ZIO.serviceWithZIO[UserSessions](_.getUserBulkhead(userId))
  
  def getSessionAndBulkhead(sessionId: SessionId) = for {
    session    <- getUserSession(sessionId)
    bulkhead   <- getUserBulkhead(session.userId)
  } yield (session, bulkhead)
  
  def deleteUserSession(sessionId: SessionId)  = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))
  def refreshUserSession(sessionId: SessionId) = ZIO.serviceWithZIO[UserSessions](_.refreshUserSession(sessionId))

  private def getUserSessionLive(sessionId: SessionId) = for {
    maybeSession <- DatabaseService.getUserSession(sessionId)
    session      <- ZIO.fromOption(maybeSession).orElseFail(Unauthorized(s"Invalid User Session."))
    authInfo     <- SpotifyAuthService
                      .requestNewAccessToken(session.refreshToken)
                      .retry(retrySchedule)
  } yield UserSession(SessionId(sessionId), UserId(session.userId), AccessToken(authInfo.accessToken))

  def startCacheReporter(cache: Cache[SessionId, Throwable, UserSession]) = {
    val reporter = CacheUtils.ZioCacheStatReporter("user_sessions", cache)
    reporter.report.repeat(Schedule.spaced(10.seconds) && Schedule.forever).forkDaemon
  }

  val retrySchedule = Schedule.recurs(2) && Schedule.exponential(50.millis) && Schedule.recurWhile {
    case SpotifyAuthError(status, _) if status.isServerError => true
    case _                                                   => false
  }
}

final case class UserSessionsLive(
    userCache: Cache[SessionId, Throwable, UserSession],
    bulkheadCache: Cache[UserId, Nothing, Bulkhead],
    databaseService: DatabaseService)
    extends UserSessions {

  override def getUserSession(sessionId: SessionId)     = getSession(sessionId)
  override def getUserBulkhead(userId: UserId)          = bulkheadCache.get(userId)
  override def refreshUserSession(sessionId: SessionId) = userCache.invalidate(sessionId) *> getSession(sessionId)

  private def getSession(sessionId: SessionId) = userCache.get(sessionId).flatMapError {
    // After we get a revocation error delete the session from DB.
    case SpotifyAuthError(_, SpotifyAuthErrorResponse.revoked) =>
      deleteUserSession(sessionId).ignore *> ZIO.succeed(Unauthorized("User Session Revoked."))
    case e                                                     => ZIO.succeed(e)
  }

  override def deleteUserSession(sessionId: SessionId) = for {
    session <- userCache.get(sessionId)
    _       <- bulkheadCache.invalidate(session.userId) <&> userCache.invalidate(sessionId)
    deleted <- databaseService.deleteUserSession(sessionId).retry(Schedule.recurs(2))
  } yield deleted
}
