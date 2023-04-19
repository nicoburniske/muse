package muse.server

import caliban.*
import caliban.execution.QueryExecution
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{AppConfig, ServerConfig, SpotifyConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.service.persist.{DatabaseService, MigrationService}
import muse.service.spotify.SpotifyService
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zio.http.model.Method
import zio.http._
import zio.http.{Http, Request, Server}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.{Tag, Task, ZIO}

// TODO: incorporate cookie signing.
val COOKIE_KEY = "XSESSION"

object MuseServer {
  val live = for {
    port               <- ZIO.serviceWith[ServerConfig](_.port)
    _                  <- MigrationService.runMigrations
    protectedEndpoints <- createProtectedEndpoints
//    corsConfig         <- getCorsConfig
    allEndpoints        = (Auth.loginEndpoints ++ protectedEndpoints)
//      @@ (MuseMiddleware.handleErrors ++ cors(corsConfig))
    metrics             = Server.serve(metricsRouter).provide(Server.defaultWithPort(9091))
    museEndpoints       = Server.serve(allEndpoints).provide(Server.defaultWithPort(port))
    _                  <- metrics <&> museEndpoints
  } yield ()

//  // TODO: Is this necessary?
//  val getCorsConfig = for {
//    domain <- ZIO.serviceWith[ServerConfig](_.domain)
//  } yield CorsConfig(
//    allowedOrigins = origin => origin.contains(domain),
//    allowedMethods = Some(Set(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS)),
//    allowedHeaders = Some(
//      Set(HttpHeaderNames.CONTENT_TYPE.toString, HttpHeaderNames.AUTHORIZATION.toString, COOKIE_KEY, "*")
//    )
//  )

  def createProtectedEndpoints = endpointsGraphQL.map {
    case (rest, websocket) =>
      (MuseMiddleware.checkAuthAddSession(Auth.logoutEndpoint ++ rest) ++ websocket)
//        MuseMiddleware.requestLoggingTrace
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

  val metricsRouter = Http.collectZIO[Request] {
    case Method.GET -> !! / "metrics" => ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  }

  // TODO: Expose this?
  lazy val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _            <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
