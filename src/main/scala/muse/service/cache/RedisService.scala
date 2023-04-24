package muse.service.cache

import muse.config.{RateLimitConfig, RedisCacheConfig}
import zio.{Duration, Reloadable, Semaphore, ZIO, ZLayer}
import zio.redis.{CodecSupplier, Redis, RedisConfig, RedisError, SingleNodeExecutor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec}

import scala.util.control.NoStackTrace

trait RedisService {
  type Error = RedisError | RedisTimeoutError

  def get[K: Schema, V: Schema](key: K): ZIO[Any, Error, Option[V]]
  def set[K: Schema, V: Schema](key: K, value: V, expiration: Option[Duration]): ZIO[Any, Error, Boolean]
  def delete[K: Schema](key: K): ZIO[Any, Error, Boolean]

  def cacheOrExecute[K: Schema, T: Schema, E <: Throwable, R](key: K, expiration: Duration)(
    retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T]

  def cacheOrExecuteBulk[K: Schema, V: Schema, E <: Throwable, R, ID](ids: List[ID], expiration: Duration)(idToKey: ID => K)(
    retrieveFresh: List[ID] => ZIO[R, E, Map[ID, V]]): ZIO[R, Throwable, List[V]]

  /**
   * Checks if the identifier is rate limited.
   * @param identifier
   *   The identifier to check.
   * @return
   *   True if the identifier is rate limited. False otherwise.
   */
  def rateLimited(identifier: String): ZIO[Any, Error, Boolean]
}

final case class RedisTimeoutError(message: String) extends NoStackTrace

object RedisService {
  def rateLimited(identifier: String) = ZIO.serviceWithZIO[RedisService](_.rateLimited(identifier))

  def layer = ZLayer.fromZIO {
    for {
      redis <- ZIO.service[Reloadable[Redis]]
      config <- ZIO.service[RateLimitConfig]
      semaphore <- Semaphore.make(1)
    } yield RedisServiceLive(redis, config, semaphore)
  }

  // Create connection. Then execute auth command.
  def redisLayer = (SingleNodeExecutor.layer ++ codecLayer) >>> Redis
    .layer.tap { redis =>
    for {
      config <- ZIO.service[RedisCacheConfig]
      _      <- redis.get.auth(config.username, config.password)
      _      <- ZIO.logInfo("Successfully authorized with Redis")
    } yield ()
  }.reloadableManual

  def connectionLayer = ZLayer.fromZIO {
    ZIO.service[RedisCacheConfig].map { config => RedisConfig(config.host, config.port) }
  }

  def codecLayer = ZLayer.succeed[CodecSupplier](new CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec
  })
}