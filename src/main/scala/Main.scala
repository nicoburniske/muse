import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory
import zio.{ZEnv, ZLayer, ZIOAppDefault}
import zio.config.typesafe.TypesafeConfig
import java.io.File

import persist.QuillContext
import server.Auth
object Main extends ZIOAppDefault {
  val clientLayer             = EventLoopGroup.auto(8) ++ ChannelFactory.auto
  val appConfig               =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfig.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }
  // TODO: add DB layer.
  val allLayers               = clientLayer ++ flattenedAppConfigLayer ++ ZEnv.live ++ QuillContext.dataSourceLayer
  val allEndpoints            = Auth.endpoints
  override def run            = {
    Server.start(8883, allEndpoints).exitCode.provideLayer(allLayers.orDie)
  }
}
