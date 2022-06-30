package muse

import caliban.*
import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import muse.config.{AppConfig, SpotifyConfig}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.table.AppUser
import muse.server.graphql.MuseGraphQL
import muse.server.{Auth, MuseMiddleware}
import muse.service.UserSessions
import muse.service.persist.{DatabaseOps, QuillContext}
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.*
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.config.typesafe.TypesafeConfig
import zio.logging.*
import zio.{Cause, LogLevel, Ref, Scope, Task, ZEnv, ZIO, ZIOAppDefault, ZLayer}

import java.io.File

val SCHEMA_FILE = "src/main/resources/schema.graphql"
val CONFIG_FILE = "src/main/resources/application.conf"

object Main extends ZIOAppDefault {
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File(CONFIG_FILE), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }
  val logger                  = console(
    logLevel = LogLevel.Info,
    format = LogFormat.colored
  ) ++ removeDefaultLoggers

  val dbLayer    = QuillContext.dataSourceLayer >>> DatabaseOps.live
  val zhttpLayer = EventLoopGroup.auto(8) ++ ChannelFactory.auto

  val config: CorsConfig =
    CorsConfig(
      allowedOrigins = _ == "localhost",
      allowedMethods = Some(Set(Method.POST, Method.GET, Method.PUT, Method.DELETE)))

  def endpointsGraphQL(interpreter: GraphQLInterpreter[MuseGraphQL.Env, CalibanError]) =
    MuseMiddleware.UserSessionAuth(Http.collectHttp[Request] {
      case _ -> !! / "api" / "graphql" =>
        ZHttpAdapter.makeHttpService(interpreter)
    })

  val logoutEndpoint = MuseMiddleware.UserSessionAuth(Http.collectZIO[Request] {
    case Method.POST -> !! / "logout" =>
      for {
        session <- MuseMiddleware.Auth.currentUser[UserSession]
        _       <- UserSessions.deleteUserSession(session.sessionCookie)
        _       <- ZIO.logInfo(
                     s"Successfully logged out user ${session.id} with cookie: ${session.sessionCookie.take(10)}")
      } yield Response.ok
  })

  def catchUnauthorized[R](http: Http[R, Throwable, Request, Response]) =
    http.catchSome { case u: Unauthorized => u.http }.tapErrorZIO {
      case e: Throwable => ZIO.logErrorCause("Server Error", Cause.fail(e))
    }

  val server = (for {
    interpreter       <- MuseGraphQL.interpreter
    _                 <- Utils.writeToFile(SCHEMA_FILE, MuseGraphQL.api.render)
    protectedEndpoints = catchUnauthorized(endpointsGraphQL(interpreter) ++ logoutEndpoint)
    _                 <- Server
                           .start(
                             8883,
                             (Auth.endpoints ++ protectedEndpoints) @@ cors(config)
                           )
                           .forever
  } yield ())
    .tapError(e => ZIO.logErrorCause(s"Failed to start server: ${e.getMessage}", Cause.fail(e)))
    .exitCode
    .provide(
      AsyncHttpClientZioBackend.layer(),
      zhttpLayer,
      flattenedAppConfigLayer,
      dbLayer,
      logger,
      UserSessions.live,
      MuseMiddleware.FiberUserSession
    )

  override def run = server
}
