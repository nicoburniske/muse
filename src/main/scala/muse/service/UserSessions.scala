package muse.service

import muse.domain.common.Types.{AccessToken, SessionId, UserId}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.auth.{AuthCodeFlowData, SpotifyAuthError, SpotifyAuthErrorResponse}
import muse.service.cache.RedisService
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.utils.Utils
import nl.vroste.rezilience.Bulkhead
import sttp.client3.SttpBackend
import zio.*
import zio.cache.{Cache, Lookup}
import zio.json.*
import zio.schema.{DeriveSchema, Schema}
import zio.stream.{ZPipeline, ZStream}

import java.io.IOException
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit

trait UserSessions {
  def getUserSession(sessionId: SessionId): IO[Throwable, UserSession]
  def deleteUserSession(sessionId: SessionId): IO[Throwable, Boolean]
  def refreshUserSession(sessionId: SessionId): IO[Throwable, UserSession]
}

object UserSessions {
  val layer = ZLayer.fromZIO {
    for {
      redisService    <- ZIO.service[RedisService]
      databaseService <- ZIO.service[DatabaseService]
      authService     <- ZIO.service[SpotifyAuthService]

      // This is to de-duplicate requests that are made within second.
      retrievalCache <- Cache.make(10000, 1.second, Lookup((sessionId: SessionId) => retrieveSession(sessionId)))
    } yield UserSessionsLive(retrievalCache, authService, redisService, databaseService)
  }

  private def retrieveSession(sessionId: SessionId) = {
    val retrySchedule = Schedule.recurs(2) && Schedule.exponential(50.millis) && Schedule.recurWhile {
      case SpotifyAuthError(status, _) if status.isServerError => true
      case _                                                   => false
    }

    val execute = for {
      maybeSession <- DatabaseService.getUserSession(sessionId)
      session      <- ZIO.fromOption(maybeSession).orElseFail(Unauthorized(s"Invalid User Session."))
      authInfo     <- SpotifyAuthService
                        .requestNewAccessToken(session.refreshToken)
                        .retry(retrySchedule)
    } yield UserSession(SessionId(sessionId), UserId(session.userId), AccessToken(authInfo.accessToken))

    execute.retry(retrySchedule)
  }

  def getUserSession(sessionId: SessionId)     = ZIO.serviceWithZIO[UserSessions](_.getUserSession(sessionId))
  def deleteUserSession(sessionId: SessionId)  = ZIO.serviceWithZIO[UserSessions](_.deleteUserSession(sessionId))
  def refreshUserSession(sessionId: SessionId) = ZIO.serviceWithZIO[UserSessions](_.refreshUserSession(sessionId))

}

final case class UserSessionsLive(
    retrievalCache: Cache[SessionId, Throwable, UserSession],
    authService: SpotifyAuthService,
    redisService: RedisService,
    databaseService: DatabaseService)
    extends UserSessions {

  val layer = ZLayer.succeed(authService) ++ ZLayer.succeed(redisService) ++ ZLayer.succeed(databaseService)

  given Schema[UserSession] = DeriveSchema.gen

  override def getUserSession(sessionId: SessionId) =
    redisService.cacheOrExecute(sessionId, 59.minutes)(retrievalCache.get(sessionId))

  override def refreshUserSession(sessionId: SessionId) =
    redisService.delete(sessionId) *> getUserSession(sessionId)

  override def deleteUserSession(sessionId: SessionId) = for {
    _       <- redisService.delete(sessionId).retry(Schedule.recurs(2))
    deleted <- databaseService.deleteUserSession(sessionId).retry(Schedule.recurs(2))
  } yield deleted
}
