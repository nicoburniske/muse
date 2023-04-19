package muse.server

import caliban.*
import caliban.execution.QueryExecution
import com.stuart.zcaffeine.Cache
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{AppConfig, ServerConfig, SpotifyConfig, SpotifyServiceConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.server.graphql.MuseGraphQL.Env
import muse.service.persist.{DatabaseService, MigrationService}
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.domain.spotify
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zio.http.model.Method
import zio.http.*
import zio.http.{Http, Request, Server}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.{Tag, Task, ZIO}

// TODO: incorporate cookie signing.
val COOKIE_KEY = "XSESSION"

// @formatter:off
type RootEnv = SttpBackend[zio.Task, Any]  &
  Cache[Any, String, spotify.PublicUser] &
  Cache[Any, String, spotify.Artist] &
  Cache[Any, String, spotify.Album] &
  SpotifyConfig & ServerConfig & SpotifyServiceConfig &
  zio.Hub[muse.domain.event.ReviewUpdate] & zio.Ref[Option[Long]] &
  RequestSession[muse.service.spotify.SpotifyService] &
  RequestSession[muse.domain.session.UserSession] & zio.Scope & muse.service.persist.DatabaseService &
  UserSessions & SpotifyAuthService & Server & PrometheusPublisher
// @formatter:on

object MuseServer {
  val live = for {
    port <- ZIO.serviceWith[ServerConfig](_.port)
    _ <- MigrationService.runMigrations
    protectedEndpoints: Http[Env, Response, Request, Response] <- createProtectedEndpoints
    allEndpoints = Auth.loginEndpoints ++ protectedEndpoints
    metrics = Server.serve(metricsRouter).provideSomeLayer[RootEnv](Server.defaultWithPort(9091))
    museEndpoints = Server.serve(allEndpoints).provideSomeLayer[RootEnv](Server.defaultWithPort(port))
    _ <- metrics <&> museEndpoints
  } yield ()

  def createProtectedEndpoints = endpointsGraphQL.map {
    case (rest, websocket) =>
      MuseMiddleware.checkAuthAddSession(Auth.logoutEndpoint ++ rest) ++ websocket
  }

  import sttp.tapir.json.zio._

  val endpointsGraphQL = for {
    interpreter <- MuseGraphQL.interpreter
  } yield (
    Http.collectRoute[Request] {
      case _ -> !! / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter, queryExecution = QueryExecution.Batched)
    },
    Http.collectRoute[Request] { case _ -> !! / "ws" / "graphql" => MuseMiddleware.Websockets.live(interpreter) }
  )

  val metricsRouter: HttpApp[PrometheusPublisher, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "metrics" => ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  }

  // TODO: Expose this?
  lazy val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _ <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
