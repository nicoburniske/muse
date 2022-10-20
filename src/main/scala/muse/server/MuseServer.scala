package muse.server

import caliban.*
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.service.persist.{DatabaseService, MigrationService}
import muse.service.spotify.SpotifyService
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service
import zio.{Tag, Task, ZIO}

// TODO: incorporate cookie signing.
val COOKIE_KEY = "XSESSION"

object MuseServer {
  val live = for {
    port               <- ZIO.serviceWith[ServerConfig](_.port)
    _                  <- MigrationService.runMigrations
    _                  <- writeSchemaToFile
    protectedEndpoints <- createProtectedEndpoints
    allEndpoints        = (Auth.loginEndpoints ++ protectedEndpoints) @@ (MuseMiddleware.handleErrors ++ cors(config))
    _                  <- service.Server.start(port, allEndpoints).forever
  } yield ()

  val config: CorsConfig = CorsConfig(allowedOrigins = _ => true)

  def createProtectedEndpoints = endpointsGraphQL.map {
    case (rest, websocket) =>
      (MuseMiddleware.checkAuthAddSession(Auth.logoutEndpoint ++ rest) ++ websocket) @@
        (MuseMiddleware.requestLoggingTrace)
  }

  val endpointsGraphQL = for {
    interpreter <- MuseGraphQL.interpreter
  } yield Http.collectHttp[Request] { case _ -> !! / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter) }
    -> Http.collectHttp[Request] { case _ -> !! / "ws" / "graphql" => MuseMiddleware.Websockets.live(interpreter) }

  // TODO: Expose this?
  lazy val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _            <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
