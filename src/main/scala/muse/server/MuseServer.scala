package muse.server

import caliban.*
import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.service.persist.DatabaseService
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
    _                  <- writeSchemaToFile
    protectedEndpoints <- createProtectedEndpoints
    allEndpoints        = Auth.loginEndpoints ++ protectedEndpoints @@ MuseMiddleware.logErrors
    _                  <- service.Server.start(port, allEndpoints).forever
  } yield ()

  val config: CorsConfig =
    CorsConfig(allowedOrigins = _ == "localhost", allowedMethods = Some(Set(Method.POST, Method.GET, Method.PUT, Method.DELETE)))

  def createProtectedEndpoints = endpointsGraphQL.map { graphqlEndpoints =>
    MuseMiddleware.checkAuthAddSession(Auth.logoutEndpoint ++ graphqlEndpoints) @@
      (MuseMiddleware.loggingMiddleware ++ cors(config))
  }

  val endpointsGraphQL = for {
    interpreter <- MuseGraphQL.interpreter
  } yield Http.collectHttp[Request] { case _ -> !! / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter) }

  val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _            <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
