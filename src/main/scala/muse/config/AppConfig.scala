package muse.config

import zio.config.*
import zio.Config
import Config._
import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfigProvider
import zio.{ZIO, ZLayer}

import java.io.File

final case class AppConfig(spotify: SpotifyConfig, sqlConfig: SqlConfig, serverConfig: ServerConfig)

object AppConfig {

  lazy val flattened = for {
    appConfigEnv   <- ZLayer.environment[AppConfig]
    spotify        <- ZLayer.succeed(appConfigEnv.get.spotify)
    sql            <- ZLayer.succeed(appConfigEnv.get.sqlConfig)
    server         <- ZLayer.succeed(appConfigEnv.get.serverConfig)
    spotifyService <- ZLayer.succeed(appConfigEnv.get.spotify.service)
  } yield appConfigEnv ++ spotify ++ sql ++ server ++ spotifyService

  lazy val layer = appConfigLayer >>> flattened

//  lazy val appConfigLayer = for {
//    config <- ZIO.attempt(ConfigFactory.load.resolve)
//    appConfig <- TypesafeConfigProvider.fromTypesafeConfig(config)
//
//                                 }
  lazy val appConfigLayer = ZLayer.fromZIO {
    read(appDescriptor from TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load.resolve))
  }

  lazy val appDescriptor: Config[AppConfig] =
    (spotifyDescriptor.nested("spotify") zip
      sqlDescriptor.nested("db") zip
      serverDescriptor.nested("server")).to[AppConfig]

  lazy val spotifyServiceDescriptor: Config[SpotifyServiceConfig] =
    (int("artist_cache_size") zip
      int("album_cache_size") zip
      int("user_cache_size") zip
      int("playlist_cache_size") zip
      int("liked_songs_cache_size")).to[SpotifyServiceConfig]

  lazy val spotifyDescriptor: Config[SpotifyConfig] =
    (string("client_id") zip
      string("client_secret") zip
      string("redirect_uri") zip
      spotifyServiceDescriptor.nested("service")).to[SpotifyConfig]

  lazy val sqlDescriptor: Config[SqlConfig] =
    (string("database") zip
      string("host") zip
      int("port") zip
      string("user") zip
      string("password")).to[SqlConfig]

  lazy val serverDescriptor: Config[ServerConfig] =
    (string("domain").optional zip
      string("frontend_url") zip
      int("port") zip
      string("schema_file") zip
      int("n_threads")).to[ServerConfig]
}
