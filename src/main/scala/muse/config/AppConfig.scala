package muse.config

import zio.ZLayer
import zio.config.*
import zio.config.typesafe.TypesafeConfig
import ConfigDescriptor.*
import ZConfig.*

import java.io.File

final case class AppConfig(spotify: SpotifyConfig, sqlConfig: SqlConfig)

object AppConfig {
  val CONFIG_FILE = "src/main/resources/application.conf"

  lazy val live = appConfigLayer.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }

  lazy val appConfigLayer =
    TypesafeConfig.fromHoconFile(new File(CONFIG_FILE), AppConfig.appDescriptor)

  val appDescriptor: ConfigDescriptor[AppConfig] =
    (nested("spotify")(spotifyDescriptor) zip
      nested("db")(sqlDescriptor)).to[AppConfig]

  val spotifyDescriptor: ConfigDescriptor[SpotifyConfig] =
    (string("client_id") zip string("client_secret") zip string("redirect_uri")).to[SpotifyConfig]

  val sqlDescriptor: ConfigDescriptor[SqlConfig] =
    (string("database") zip
      string("host") zip
      ConfigDescriptor.int("port") zip
      string("user") zip
      string("password")).to[SqlConfig]
}
