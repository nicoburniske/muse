package muse.config

import com.typesafe.config.ConfigFactory
import zio.Config.*
import zio.config.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.redis.RedisConfig
import zio.{Config, ZIO, ZLayer}

import java.io.File

final case class AppConfig(
    spotify: SpotifyConfig,
    sqlConfig: SqlConfig,
    serverConfig: ServerConfig,
    redisConfig: RedisCacheConfig)

final case class ServerConfig(domain: Option[String], frontendUrl: String, port: Int, schemaFile: String, nThreads: Int)
final case class SpotifyConfig(clientID: String, clientSecret: String, redirectURI: String, service: SpotifyServiceConfig)
final case class SpotifyServiceConfig(
    artistCacheSize: Int,
    albumCacheSize: Int,
    userCacheSize: Int,
    playlistCacheSize: Int,
    likedSongsCacheSize: Int
)
final case class SqlConfig(database: String, host: String, port: Int, user: String, password: String)
final case class RedisCacheConfig(host: String, port: Int, username: String, password: String)

object AppConfig {

  lazy val flattened = for {
    appConfigEnv   <- ZLayer.environment[AppConfig]
    spotify        <- ZLayer.succeed(appConfigEnv.get.spotify)
    sql            <- ZLayer.succeed(appConfigEnv.get.sqlConfig)
    server         <- ZLayer.succeed(appConfigEnv.get.serverConfig)
    spotifyService <- ZLayer.succeed(appConfigEnv.get.spotify.service)
    redisConfig    <- ZLayer.succeed(appConfigEnv.get.redisConfig)
    _              <- ZLayer.fromZIO(ZIO.logInfo(s"Loaded config: $redisConfig"))
  } yield appConfigEnv ++ spotify ++ sql ++ server ++ spotifyService ++ redisConfig

  lazy val layer = appConfigLayer >>> flattened

  lazy val appConfigLayer = ZLayer.fromZIO {
    read(appDescriptor from TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load.resolve))
  }

  lazy val appDescriptor: Config[AppConfig] =
    (spotifyDescriptor.nested("spotify") zip
      sqlDescriptor.nested("db") zip
      serverDescriptor.nested("server") zip
      redisDescriptor.nested("redis")).to[AppConfig]

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

  lazy val redisDescriptor: Config[RedisCacheConfig] =
    (string("host") zip
      int("port") zip
      string("username") zip
      string("password")).to[RedisCacheConfig]

  lazy val serverDescriptor: Config[ServerConfig] =
    (string("domain").optional zip
      string("frontend_url") zip
      int("port") zip
      string("schema_file") zip
      int("n_threads")).to[ServerConfig]
}
