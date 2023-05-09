package muse.service

import muse.domain.common.Types.{AccessToken, RefreshToken, SessionId, UserId}
import muse.domain.session.{UserSession, SpotifyData}
import muse.domain.spotify.auth.{AuthCodeFlowData, SpotifyAuthError, SpotifyAuthErrorResponse}
import muse.service.cache.RedisService
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.utils.Utils
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

trait UserSessionService {
  def getUserSession(sessionId: SessionId): IO[Throwable, Option[UserSession]]
  def getFreshAccessToken(sessionId: SessionId): IO[Throwable, Option[AccessToken]]
  def refreshAndGetUserSession(sessionId: SessionId): IO[Throwable, Option[UserSession]]
  def deleteUserSession(sessionId: SessionId): IO[Throwable, Boolean]
  def isRateLimited(userId: UserId): IO[Throwable, Boolean]
}

object UserSessionService {
  val layer = ZLayer.fromZIO {
    for {
      redisService    <- ZIO.service[RedisService]
      databaseService <- ZIO.service[DatabaseService]
      authService     <- ZIO.service[SpotifyAuthService]

      // This is to de-duplicate requests that are made within second.
      retrievalCache <- Cache.make(10000, 1.second, Lookup((sessionId: SessionId) => retrieveSession(sessionId)))
    } yield UserSessionsLive(retrievalCache, authService, redisService, databaseService)
  }

  def retrieveSession(sessionId: SessionId) = {
    val retry = Schedule.recurs(4) && Schedule.exponential(50.millis).jittered

    val retrySpotify = retry && Schedule.recurWhile {
      case SpotifyAuthError(status, _) if status.isServerError => true
      case _                                                   => false
    }

    val execute = DatabaseService.getUserSession(sessionId).flatMap {
      case None          => ZIO.succeed(None)
      case Some(session) =>
        for {
          authInfo <- SpotifyAuthService
                        .requestNewAccessToken(session.refreshToken)
                        .retry(retrySpotify)
          now      <- ZIO.clock.flatMap(_.instant)
        } yield Some(
          UserSession(
            SessionId(sessionId),
            UserId(session.userId),
            SpotifyData(
              AccessToken(authInfo.accessToken),
              RefreshToken(session.refreshToken),
              now.plusSeconds(authInfo.expiresIn)
            )
          )
        )
    }

    execute.retry(retry)
  }

  def getUserSession(sessionId: SessionId)           = ZIO.serviceWithZIO[UserSessionService](_.getUserSession(sessionId))
  def getFreshAccessToken(sessionId: SessionId)      = ZIO.serviceWithZIO[UserSessionService](_.getFreshAccessToken(sessionId))
  def refreshAndGetUserSession(sessionId: SessionId) =
    ZIO.serviceWithZIO[UserSessionService](_.refreshAndGetUserSession(sessionId))
  def deleteUserSession(sessionId: SessionId)        = ZIO.serviceWithZIO[UserSessionService](_.deleteUserSession(sessionId))
  def isRateLimited(userId: UserId)                  = ZIO.serviceWithZIO[UserSessionService](_.isRateLimited(userId))
}

final case class UserSessionsLive(
    retrievalCache: Cache[SessionId, Throwable, Option[UserSession]],
    authService: SpotifyAuthService,
    redisService: RedisService,
    databaseService: DatabaseService)
    extends UserSessionService {

  val layer = ZLayer.succeed(authService) ++ ZLayer.succeed(redisService) ++ ZLayer.succeed(databaseService)

  given Schema[UserSession] = DeriveSchema.gen

  override def getUserSession(sessionId: SessionId) =
    redisService.cacheOrExecute(sessionId, 59.minutes)(retrievalCache.get(sessionId))

  override def getFreshAccessToken(sessionId: SessionId) =
    UserSessionService.retrieveSession(sessionId).map(_.map(_.spotifyData.accessToken)).provide(layer)

  override def refreshAndGetUserSession(sessionId: SessionId) =
    deleteUserSession(sessionId) *> getUserSession(sessionId)

  override def deleteUserSession(sessionId: SessionId) = for {
    _       <- redisService.delete(sessionId).retry(Schedule.recurs(2))
    deleted <- databaseService.deleteUserSession(sessionId).retry(Schedule.recurs(2))
  } yield deleted

  override def isRateLimited(userId: UserId) = redisService
    .rateLimited(userId).retry(Schedule.recurs(3) && Schedule.spaced(15.millis).jittered)
}
