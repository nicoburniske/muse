import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory
import zio.{ZEnv, ZLayer, ZIOAppDefault}
import zio.config.typesafe.TypesafeConfig
import java.io.File

import persist.QuillContext
import server.Auth
import persist.DatabaseQueries
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend.apply
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
object Main extends ZIOAppDefault {
  val clientLayer             = EventLoopGroup.auto(8) ++ ChannelFactory.auto
  val appConfigLayer          =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

  val dbLayer = QuillContext.dataSourceLayer >+> DatabaseQueries.live

  val allLayers = AsyncHttpClientZioBackend
    .layer() ++ clientLayer ++ flattenedAppConfigLayer ++ ZEnv.live ++ QuillContext.dataSourceLayer ++ dbLayer

  val allEndpoints = Auth.endpoints

  val server       = Server.start(8883, allEndpoints).exitCode.provideLayer(allLayers.orDie)
  override def run = {
    server
  }
}
