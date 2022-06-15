import muse.config.AppConfig
import muse.domain.tables.AppUser
import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory
import zio.{Ref, ZEnv, ZIOAppDefault, ZLayer}
import zio.config.typesafe.TypesafeConfig

import java.io.File
import muse.persist.QuillContext
import muse.server.Auth
import muse.persist.DatabaseQueries
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend

object Main extends ZIOAppDefault {
  val clientLayer             = EventLoopGroup.auto(8) ++ ChannelFactory.auto
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

  val dbLayer = QuillContext.dataSourceLayer >+> DatabaseQueries.live
  val users   = ZLayer.fromZIO(Ref.make(Map.empty[String, AppUser]))

  val allLayers =
    AsyncHttpClientZioBackend
      .layer() ++ clientLayer ++ flattenedAppConfigLayer ++ ZEnv.live ++ dbLayer ++ users

  val allEndpoints = Auth.endpoints

  val server = Server.start(8883, allEndpoints).exitCode.provideLayer(allLayers.orDie)

  override def run = server
}
