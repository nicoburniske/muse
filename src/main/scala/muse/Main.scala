package muse

import caliban.*
import muse.config.{AppConfig, SpotifyConfig}
import muse.domain.session.UserSession
import muse.domain.tables.AppUser
import muse.server.MuseMiddleware.Unauthorized
import muse.server.graphql.MuseGraphQL
import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory
import zhttp.http.{Http, Method, Request}
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.*
import zio.{Ref, Scope, Task, ZEnv, ZIO, ZIOAppDefault, ZLayer}
import zio.config.typesafe.TypesafeConfig

import java.io.File
import muse.server.{Auth, MuseMiddleware}
import muse.service.UserSessions
import muse.service.persist.{DatabaseQueries, QuillContext}
import muse.service.spotify.SpotifyService
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend

object Main extends ZIOAppDefault {
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

  val dbLayer    = QuillContext.dataSourceLayer >>> DatabaseQueries.live
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
    interpreter       <- MuseGraphQL.api.interpreter
    _                 <- ZIO.logInfo(MuseGraphQL.api.render) // TODO: write to file
    protectedEndpoints = catchUnauthorized(endpointsGraphQL(interpreter) ++ logoutEndpoint)
    _                 <- Server
                           .start(
                             8883,
                             (Auth.endpoints ++ protectedEndpoints) @@ cors(config)
                           )
                           .forever
  } yield ())
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
