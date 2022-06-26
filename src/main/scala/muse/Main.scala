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
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.http.*
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.config.typesafe.TypesafeConfig
import zio.{Ref, Scope, Task, ZEnv, ZIO, ZIOAppDefault, ZLayer}

import java.io.File

object Main extends ZIOAppDefault {
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

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

  def catchUnauthorized[R](http: Http[R, Throwable, Request, Response]) = http.catchSome {
    case u: Unauthorized => u.http
  }

  val server = (for {
    interpreter       <- MuseGraphQL.interpreter
    _                 <- ZIO.logInfo(MuseGraphQL.api.render) // TODO: write to file
    protectedEndpoints = catchUnauthorized(endpointsGraphQL(interpreter) ++ logoutEndpoint)
    _                 <- Server
                           .start(
                             8883,
                             (Auth.endpoints ++ protectedEndpoints) @@ cors(config)
                           )
                           .forever
  } yield ())
    .tapError(e => ZIO.logError(s"Failed to start server: + ${e.toString}"))
    .exitCode
    .provide(
      AsyncHttpClientZioBackend.layer(),
      zhttpLayer,
      flattenedAppConfigLayer,
      dbLayer,
      UserSessions.live,
      MuseMiddleware.FiberUserSession
    )

  override def run = server
}
