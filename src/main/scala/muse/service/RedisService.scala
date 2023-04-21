package muse.service

import zio.redis.{CodecSupplier, Redis, RedisError, RedisExecutor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec}
import zio.{Duration, ZIO, ZLayer, durationInt}

case class RedisService(redis: Redis) {
  val layer = ZLayer.succeed(redis)

  case object RedisTimeoutError extends Throwable("Redis timeout")

  def cacheOrExecute[T: Schema, E <: Throwable, R](key: String, expiration: Duration)(
      retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T] =
    for {
      maybeValue <- redis.get(key).returning[T].timeoutFail(RedisTimeoutError)(5.seconds)
      value      <- maybeValue match {
                      case Some(value) => ZIO.succeed(value)
                      case None        =>
                        for {
                          value <- retrieveFresh
                          _     <- redis.set(key, value, Some(expiration)).forkDaemon
                        } yield value
                    }
    } yield value

  def cacheOrExecuteBulk[T: Schema, E <: Throwable, R](keys: List[String])(
      retrieveFresh: List[String] => ZIO[R, E, Map[String, T]]): ZIO[R, Throwable, Map[String, T]] =
    keys match
      case Nil            => ZIO.succeed(Map.empty)
      case ::(head, next) =>
        for {
          values: zio.Chunk[Option[T]] <- redis
                                            .mGet(head, next*).returning[T]
                                            .timeoutFail(RedisTimeoutError)(5.seconds)
          zipped                        = keys zip values
          missing                       = zipped.filter(_._2.isEmpty).map(_._1)
          retrieved                    <- retrieveFresh(missing)
          // Set new values in redis.
          _                            <- retrieved.toList match
                                            case Nil            => ZIO.unit
                                            case ::(head, next) => redis.mSet(head, next*).forkDaemon

        } yield retrieved ++ zipped.filter(_._2.isDefined).map { case (k, v) => k -> v.get }.toMap
}

object RedisService {

  def redisLayer   = (RedisExecutor.layer ++ codecLayer) >>> Redis.layer
  def serviceLayer = ZLayer.fromFunction(RedisService.apply)

  def codecLayer = ZLayer.succeed[CodecSupplier](RedisCodecSupplier)

  object RedisCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec
  }
}
