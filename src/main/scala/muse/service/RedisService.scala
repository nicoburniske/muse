package muse.service

import muse.config.RedisCacheConfig
import zio.redis.{CodecSupplier, Redis, RedisConfig, RedisError, RedisExecutor, SingleNodeExecutor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec, ProtobufCodec}
import zio.{Duration, ZIO, ZLayer, durationInt}

case class RedisService(redis: Redis) {
  val layer = ZLayer.succeed(redis)

  case class RedisTimeoutError(detail: String) extends Throwable(s"Redis timeout $detail")

  def cacheOrExecute[T: Schema, E <: Throwable, R](key: String, expiration: Duration)(
      retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T] =
    for {
      maybeValue <- redis.get(key).returning[T].timeoutTo(None)(identity)(1.seconds)
      value      <- maybeValue match {
                      case Some(value) => ZIO.succeed(value)
                      case None        =>
                        for {
                          value <- retrieveFresh
                          _     <- redis
                                     .set(key, value, Some(expiration))
                                     .timeoutFail(RedisTimeoutError("set"))(1.seconds)
                                     .tapError(e => ZIO.logError(s"Failed to set value in Redis. ${e.toString} $key"))
                                     .forkDaemon
                        } yield value
                    }
    } yield value

  def cacheOrExecuteBulk[T: Schema, E <: Throwable, R, K](ids: List[K], expiration: Duration)(idToKey: K => String)(
      retrieveFresh: List[K] => ZIO[R, E, Map[K, T]]): ZIO[R, Throwable, List[T]] =
    ids match
      case Nil => ZIO.succeed(Nil)
      case ids =>
        for {
          values: List[Option[T]] <- ZIO
                                       .foreachPar(ids.map(idToKey))(redis.get(_).returning[T])
                                       .timeoutTo(List.fill(ids.size)(None))(identity)(1.seconds)
          _                       <- ZIO.logError("Values retrieved are not the same size as ids").when(values.size != ids.size)
          zipped                   = ids zip values
          missing                  = zipped.filter(_._2.isEmpty).map(_._1)
          retrieved               <- if (missing.isEmpty) ZIO.succeed(Map.empty) else retrieveFresh(missing)
          // Set new values in redis.
          _                       <- retrieved.toList.map { case (id, v) => idToKey(id) -> v } match
                                       case Nil     => ZIO.unit
                                       case entries =>
                                         // To keep the expiration time, we need to set the values one by one.
                                         ZIO
                                           .foreachPar(entries) { case (key, value) => redis.set(key, value, Some(expiration)) }
                                           .timeoutFail(RedisTimeoutError("mSet"))(1.seconds)
                                           .tapError { e =>
                                             ZIO.logError(s"Failed to set values in Redis. ${e.toString}. ${entries.map(_._1).mkString(",")}}")
                                           }.forkDaemon
        } yield retrieved.values.toList ++ values.flatten
}

object RedisService {

  // Create connection. Then execute auth command.
  def redisLayer = connectionLayer >>> (SingleNodeExecutor.layer ++ codecLayer) >>> Redis.layer.tap { redis =>
    for {
      config <- ZIO.service[RedisCacheConfig]
      _      <- redis.get.auth(config.username, config.password)
      _      <- ZIO.logInfo("Successfully authorized with Redis")
    } yield ()
  }

  def connectionLayer = ZLayer.fromZIO {
    ZIO.service[RedisCacheConfig].map { config => RedisConfig(config.host, config.port) }
  }

  def serviceLayer = ZLayer.fromFunction(RedisService.apply)

  def codecLayer = ZLayer.succeed[CodecSupplier](RedisCodecSupplier)

  object RedisCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec
  }
}
