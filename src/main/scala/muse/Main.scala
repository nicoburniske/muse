package muse

import caliban.*
import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import muse.config.{AppConfig, ServerConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.server.{Auth, MuseMiddleware, MuseServer}
import muse.service.persist.{DatabaseService, MigrationService, QuillContext}
import muse.service.spotify.{RateLimitRef, SpotifyAuthService, SpotifyCache}
import muse.service.{RedisService, RequestSession, ReviewUpdates, UserSessions}
import muse.utils.Utils
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.Duration.*
import zio.http.{Client, Server}
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.metrics.connectors.MetricsConfig
import zio.redis.{Redis, RedisExecutor}
import zio.{Cause, Duration, LogLevel, Ref, Runtime, Schedule, Scope, Task, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

import java.time.format.DateTimeFormatter

object Main extends ZIOAppDefault {
  override def run = MuseServer
    .live
    .provide(
      ZLayer.Debug.mermaid,
      Scope.default,
      AsyncHttpClientZioBackend.layer(),
      serverConfig,
      Client.default,
      // Spotify layers
      SpotifyAuthService.layer,
      SpotifyCache.layer,
      RateLimitRef.layer,
      // Muse layers.
      AppConfig.layer,
      DatabaseService.layer,
      RedisService.layer,
      RedisService.redisLayer,
      MigrationService.layer,
      UserSessions.layer,
      ReviewUpdates.hub,
      RequestSession.userSessionLayer,
      RequestSession.spotifySessionLayer,
      QuillContext.dataSourceLayer,
      // Metrics.
      zio.metrics.connectors.prometheus.publisherLayer,
      zio.metrics.connectors.prometheus.prometheusLayer,
      metricsConfig,
      Runtime.enableRuntimeMetrics
    )
    .tapErrorCause(e => ZIO.logErrorCause(s"Failed to start server ${e.prettyPrint}", e))
    .exitCode

  val serverConfig = ZLayer.fromFunction { (config: ServerConfig) => Server.defaultWithPort(config.port) }.flatten

  val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  val logFormat = {
    import zio.logging.LogFormat._

    val userId = LogFormat.annotation("user_id")

    label("timestamp", timestamp(DateTimeFormatter.ISO_LOCAL_DATE_TIME).fixed(28)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("thread", fiberId).color(LogColor.WHITE) |-|
      (space + label("user_id", userId).highlight) |-|
      label("message", quoted(line)).highlight +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

  }

  val logLayer           = Runtime.removeDefaultLoggers >>> SLF4J.slf4j(logFormat)
  override val bootstrap = logLayer
}
