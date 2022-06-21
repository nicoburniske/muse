package muse

import caliban.*
import muse.config.{AppConfig, SpotifyConfig}
import muse.domain.tables.AppUser
import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory
import zhttp.http.{Http, Method, Request}
import zhttp.http.*
import zhttp.http.Middleware.cors
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.*
import zio.{Ref, Scope, ZEnv, ZIO, ZIOAppDefault, ZLayer}
import zio.config.typesafe.TypesafeConfig

import java.io.File
import muse.server.{ApiGraphQL, Auth, MuseMiddleware, Protected}
import muse.service.UserSessions
import muse.service.persist.{DatabaseQueries, QuillContext}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend

object Main extends ZIOAppDefault {
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

  val dbLayer    = QuillContext.dataSourceLayer >>> DatabaseQueries.live
  val zhttpLayer = EventLoopGroup.auto(8) ++ ChannelFactory.auto
  val allLayers  =
    AsyncHttpClientZioBackend.layer() ++
      zhttpLayer ++
      flattenedAppConfigLayer ++
      ZEnv.live ++
      dbLayer ++
      UserSessions.live

  val config: CorsConfig =
    CorsConfig(
      allowedOrigins = _ == "localhost",
      allowedMethods = Some(Set(Method.GET, Method.PUT, Method.DELETE)))

  val allEndpoints = (Auth.endpoints ++ Protected.endpoints) @@ cors(config)

  val server = (for {
    interpreter <- ApiGraphQL.api.interpreter
    _           <- Server
                     .start(
                       8883,
                       allEndpoints ++
                         Http.collectHttp[Request] {
                           case _ -> !! / "api" / "graphql" =>
                             MuseMiddleware.userSessionAuth(
                               ZHttpAdapter.makeHttpService(interpreter)
                             )
                         }
                     )
                     .forever
  } yield ())
    .exitCode
    .provide(
      AsyncHttpClientZioBackend.layer(),
      zhttpLayer,
      flattenedAppConfigLayer,
      ZEnv.live,
      dbLayer,
      UserSessions.live,
      MuseMiddleware.HttpLayer,
      ZLayer.Debug.mermaid
    )
  //    .provideLayer(allLayers.orDie)
  //    .provideLayer(MuseMiddleware.http)

  //  val server = Server.start(8883, allEndpoints).forever.exitCode.provideLayer(allLayers.orDie)

  override def run = server
}
