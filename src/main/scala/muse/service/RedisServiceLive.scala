package muse.service

import muse.config.RedisCacheConfig
import zio.redis.{CodecSupplier, Redis, RedisConfig, RedisError, RedisExecutor, SingleNodeExecutor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec, ProtobufCodec}
import zio.{Duration, Schedule, Semaphore, ZIO, ZLayer, durationInt}

trait RedisService {
  def cacheOrExecute[T: Schema, E <: Throwable, R](key: String, expiration: Duration)(
      retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T]

  def cacheOrExecuteBulk[T: Schema, E <: Throwable, R, K](ids: List[K], expiration: Duration)(idToKey: K => String)(
      retrieveFresh: List[K] => ZIO[R, E, Map[K, T]]): ZIO[R, Throwable, List[T]]
}

object RedisService {

  // Create connection. Then execute auth command.
  def redisLayer = connectionLayer >>> (SingleNodeExecutor.layer ++ codecLayer) >>> Redis.layer.tap { redis =>
    for {
      config <- ZIO.service[RedisCacheConfig]
      _      <- redis.get.auth(config.username, config.password)
      _      <- ZIO.logInfo("Successfully authorized with Redis")
      _      <- redis
                  .get.ping().timeout(1.second).tap {
                    case Some(_) => ZIO.logInfo("Redis ping success.")
                    case None    => ZIO.logError("Redis ping failure.")
                  }.schedule(Schedule.spaced(5.minutes) && Schedule.forever).forkDaemon
    } yield ()
  }

  def connectionLayer = ZLayer.fromZIO {
    ZIO.service[RedisCacheConfig].map { config => RedisConfig(config.host, config.port) }
  }

  def layer = ZLayer.fromZIO {
    for {
      redis     <- ZIO.service[Redis]
      semaphore <- Semaphore.make(1)
    } yield RedisServiceLive(redis, semaphore)
  }

  def codecLayer = ZLayer.succeed[CodecSupplier](RedisCodecSupplier)

  object RedisCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec
  }
}

final case class RedisServiceLive(redis: Redis, semaphore: Semaphore) extends RedisService {
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
                                     .tapError(e => ZIO.logError(s"Failed to set value in Redis. ${e.toString} $key") *> resetConnection)
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
                                             ZIO.logError(s"Failed to set values in Redis. ${e.toString}.") *> resetConnection
                                           }.forkDaemon
        } yield retrieved.values.toList ++ values.flatten

  def resetConnection = ZIO.whenZIO(semaphore.available.map(_ > 0)) {
    semaphore.withPermit {
      for {
        _      <- ZIO.logInfo("Resetting Redis connection")
        result <- redis.ping().timeout(1.second)
        _      <- result.fold(ZIO.logError(s"Redis ping failure. Connection will be reset.")) { _ =>
                    ZIO.logInfo("Redis ping success. No reset.")
                  }
        _      <- result.fold {
                    redis
                      .reset.timeoutFail(RedisTimeoutError("Reset Connection"))(1.second).foldZIO(
                        e => ZIO.logError(s"Redis reset failure. ${e.toString}"),
                        _ => ZIO.logInfo("Redis reset success!")
                      )
                  }(_ => ZIO.unit)
      } yield ()
    }
  }
}
