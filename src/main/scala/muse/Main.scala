package muse

import caliban.*
import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import muse.config.AppConfig
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
import zio.{Cause, Duration, LogLevel, Ref, Schedule, Scope, Task, ZIO, ZIOAppDefault, ZLayer}
import zio.Duration.*
import zio.durationInt

import java.io.File

val SCHEMA_FILE = "src/main/resources/graphql/schema.graphql"

object Main extends ZIOAppDefault {
  val server = for {
    interpreter <- MuseGraphQL.interpreter
    _ <- Utils.writeToFile(SCHEMA_FILE, MuseGraphQL.api.render)
    protectedEndpoints = createProtectedEndpoints(interpreter)
    _ <- Server
      .start(
        8883,
        (Auth.loginEndpoints ++ protectedEndpoints) @@ cors(config)
      )
      .forever
  } yield ()

  override def run = server
    .provide(
      Scope.default,
      AsyncHttpClientZioBackend.layer(),
      zhttpLayer,
      AppConfig.live,
      dbLayer,
      logger,
      UserSessions.live,
      MuseMiddleware.FiberUserSession
    )
    .tapError(e => ZIO.logErrorCause(s"Failed to start server: ${e.getMessage}", Cause.fail(e)))
    .exitCode

  val logger = console(
    logLevel = LogLevel.Info,
    format = LogFormat.colored
  ) ++ removeDefaultLoggers

  // Exponential backoff retry strategy for connecting to Postgres DB.
  val expUpTo10 = Schedule.exponential(1.second) && Schedule.recurs(10)
  val dbLayer = (QuillContext.dataSourceLayer >>> DatabaseOps.live).retry(expUpTo10)

  // TODO: move to config file.
  val zhttpLayer = EventLoopGroup.auto(8) ++ ChannelFactory.auto

  lazy val config: CorsConfig =
    CorsConfig(
      allowedOrigins = _ == "localhost",
      allowedMethods = Some(Set(Method.POST, Method.GET, Method.PUT, Method.DELETE)))

  def createProtectedEndpoints(interpreter: GraphQLInterpreter[MuseGraphQL.Env, CalibanError]) =
    catchUnauthorizedAndLogErrors(endpointsGraphQL(interpreter) ++ Auth.logoutEndpoint)

  def catchUnauthorizedAndLogErrors[R](http: Http[R, Throwable, Request, Response]) =
    http
      .catchSome { case u: Unauthorized => u.http }
      .tapErrorZIO { case e: Throwable => ZIO.logErrorCause("Server Error", Cause.fail(e)) }

  def endpointsGraphQL(interpreter: GraphQLInterpreter[MuseGraphQL.Env, CalibanError]) =
    MuseMiddleware.UserSessionAuth(Http.collectHttp[Request] {
      case _ -> !! / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter)
    })

}
