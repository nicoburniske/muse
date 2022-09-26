package muse.server

import caliban.*
import muse.config.ServerConfig
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service
import zio.ZIO

// TODO: incorporate cookie signing.
val COOKIE_KEY = "XSESSION"

object MuseServer {
  val live = for {
    interpreter <- MuseGraphQL.interpreter
    serverConfig <- ZIO.service[ServerConfig]
    _ <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
    allEndpoints = (Auth.loginEndpoints ++ createProtectedEndpoints(interpreter)) @@
      (cors(config) ++ MuseMiddleware.logErrors)
    _ <- service
      .Server
      .start(
        serverConfig.port,
        allEndpoints
      )
      .forever
  } yield ()

  val config: CorsConfig =
    CorsConfig(
      allowedOrigins = _ == "localhost",
      allowedMethods = Some(Set(Method.POST, Method.GET, Method.PUT, Method.DELETE)))

  def createProtectedEndpoints(interpreter: GraphQLInterpreter[MuseGraphQL.Env, CalibanError]) =
    MuseMiddleware.userSessionAuth(endpointsGraphQL(interpreter) ++ Auth.logoutEndpoint) @@
      (MuseMiddleware.loggingMiddleware ++ MuseMiddleware.catchUnauthorized)

  def endpointsGraphQL[R](interpreter: GraphQLInterpreter[R & SpotifyService, CalibanError]) =
    Http.collectHttp[Request] {
      case _ -> !! / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter)
    }
}
