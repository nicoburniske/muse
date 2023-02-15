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
import muse.service.spotify.{SpotifyAuthService, SpotifyCache, RateLimitRef}
import muse.service.{RequestSession, ReviewUpdates, UserSessions}
import muse.utils.Utils
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.*
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.Duration.*
import zio.config.typesafe.TypesafeConfig
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.metrics.connectors.MetricsConfig
import zio.{Cause, Duration, LogLevel, Ref, Runtime, Schedule, Scope, Task,  ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

object Main extends ZIOAppDefault {
  override def run = MuseServer
    .live
    .provide(
      ZLayer.Debug.mermaid,
      Scope.default,
      AsyncHttpClientZioBackend.layer(),
      ChannelFactory.auto,
      eventLoopGroupLayer,
      // Spotify layers
      SpotifyAuthService.layer,
      SpotifyCache.layer,
      RateLimitRef.layer,
      // Muse layers.
      AppConfig.layer,
      DatabaseService.layer,
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

  val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  val eventLoopGroupLayer = for {
    serverConfig <- ZLayer.environment[ServerConfig]
    http         <- EventLoopGroup.auto(serverConfig.get.nThreads)
  } yield http

  val logLayer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogLevel.Info, LogFormat.colored)

  override val bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Any] = logLayer
}
