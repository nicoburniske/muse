package muse.config

import zio.config.*
import ConfigDescriptor.*
import ZConfig.*
import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfig
import zio.{ZIO, ZLayer}

import java.io.File

final case class AppConfig(spotify: SpotifyConfig, sqlConfig: SqlConfig, serverConfig: ServerConfig)

object AppConfig {

  lazy val flattened = for {
    appConfigEnv <- ZLayer.environment[AppConfig]
    spotify      <- ZLayer.succeed(appConfigEnv.get.spotify)
    sql          <- ZLayer.succeed(appConfigEnv.get.sqlConfig)
    server       <- ZLayer.succeed(appConfigEnv.get.serverConfig)
  } yield spotify ++ sql ++ server ++ appConfigEnv

  lazy val layer = appConfigLayer >>> flattened

  lazy val appConfigLayer = TypesafeConfig.fromTypesafeConfig(ZIO.attempt(ConfigFactory.load.resolve), appDescriptor)

  lazy val appDescriptor: ConfigDescriptor[AppConfig] =
    (nested("spotify")(spotifyDescriptor) zip
      nested("db")(sqlDescriptor) zip
      nested("server")(serverDescriptor)).to[AppConfig]

  lazy val spotifyDescriptor: ConfigDescriptor[SpotifyConfig] =
    (string("client_id") zip
      string("client_secret") zip
      string("redirect_uri")).to[SpotifyConfig]

  lazy val sqlDescriptor: ConfigDescriptor[SqlConfig] =
    (string("database") zip
      string("host") zip
      int("port") zip
      string("user") zip
      string("password")).to[SqlConfig]

  lazy val serverDescriptor: ConfigDescriptor[ServerConfig] =
    (string("frontend_url") zip
      int("port") zip
      string("schema_file") zip
      string("user_sessions_file") zip
      int("n_threads")).to[ServerConfig]
}
