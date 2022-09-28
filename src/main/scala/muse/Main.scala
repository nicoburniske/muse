package muse

import caliban.*
import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import muse.config.{AppConfig, ServerConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.table.AppUser
import muse.server.graphql.MuseGraphQL
import muse.server.{Auth, MuseMiddleware, MuseServer}
import muse.service.{RequestSession, UserSessions}
import muse.service.persist.{DatabaseService, QuillContext}
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
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
import zio.{Cause, Duration, LogLevel, Ref, Runtime, Schedule, Scope, Task, ZIO, ZIOAppDefault, ZLayer}

object Main extends ZIOAppDefault {
  override def run = MuseServer
    .live
    .provide(
      ZLayer.Debug.mermaid,
      Scope.default,
      AsyncHttpClientZioBackend.layer(),
      ChannelFactory.auto,
      // Muse layers.
      SpotifyAuthService.layer,
      AppConfig.layer,
      DatabaseService.layer,
      UserSessions.layer,
      RequestSession.layer,
      QuillContext.dataSourceLayer,
      logLayer,
      zhttpLayer
    )
    .tapError(e => ZIO.logErrorCause(s"Failed to start server: ${e.getMessage}", Cause.fail(e)))
    .exitCode

  val zhttpLayer = for {
    serverConfig <- ZLayer.environment[ServerConfig]
    http         <- EventLoopGroup.auto(serverConfig.get.nThreads)
  } yield http

  val logLayer = removeDefaultLoggers >>> SLF4J.slf4j(LogLevel.Debug, LogFormat.colored)
}
