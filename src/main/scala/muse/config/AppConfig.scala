package muse.config

import com.typesafe.config.ConfigFactory
import zio.Config.*
import zio.config.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.redis.RedisConfig
import zio.{Config, Duration, ZIO, ZLayer}

import java.io.File

final case class AppConfig(
    spotify: SpotifyConfig,
    db: SqlConfig,
    server: ServerConfig,
    redis: RedisCacheConfig,
    nats: NatsConfig,
    rateLimit: RateLimitConfig)

final case class ServerConfig(domain: Option[String], frontendUrl: zio.http.URL, port: Int, schemaFile: String)
final case class SpotifyConfig(clientID: String, clientSecret: String, redirectURI: String, service: SpotifyServiceConfig)
final case class SqlConfig(database: String, host: String, port: Int, user: String, password: String)
final case class RedisCacheConfig(host: String, port: Int, username: String, password: String)
final case class NatsConfig(url: String)
final case class RateLimitConfig(maxRequests: Int, timeWindow: Duration)

// Unused for now.
final case class SpotifyServiceConfig(
    artistCacheSize: Int,
    albumCacheSize: Int,
    userCacheSize: Int,
    playlistCacheSize: Int,
    likedSongsCacheSize: Int
)

object AppConfig {

  lazy val flattened =
    ZLayer.service[AppConfig].project(_.spotify) ++
      ZLayer.service[AppConfig].project(_.db) ++
      ZLayer.service[AppConfig].project(_.server) ++
      ZLayer.service[AppConfig].project(_.redis) ++
      ZLayer.service[AppConfig].project(_.rateLimit) ++
      ZLayer.service[AppConfig].project(_.nats)

  lazy val layer = appConfigLayer >>> flattened

  lazy val appConfigLayer = ZLayer.fromZIO {
    read(appDescriptor from TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load.resolve).snakeCase)
  }

  import zio.config.magnolia.*

  given Config[zio.http.URL] =
    string.mapOrFail { f =>
      zio.http.URL.decode(f).left.map(e => Config.Error.InvalidData(message = s"Invalid URL: ${e.getMessage}"))
    }

  val appDescriptor = deriveConfig[AppConfig]

}
