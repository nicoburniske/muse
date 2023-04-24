package muse.service

import muse.config.{RateLimitConfig, RedisCacheConfig}
import zio.redis.{CodecSupplier, Redis, RedisConfig, RedisError, RedisExecutor, SingleNodeExecutor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec, ProtobufCodec}
import zio.{Chunk, Clock, Duration, Reloadable, Schedule, Semaphore, ZIO, ZLayer, durationInt}

trait RedisService {
  def cacheOrExecute[T: Schema, E <: Throwable, R](key: String, expiration: Duration)(
      retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T]

  def cacheOrExecuteBulk[T: Schema, E <: Throwable, R, K](ids: List[K], expiration: Duration)(idToKey: K => String)(
      retrieveFresh: List[K] => ZIO[R, E, Map[K, T]]): ZIO[R, Throwable, List[T]]

  /**
   * Checks if the identifier is rate limited.
   * @param identifier
   *   The identifier to check.
   * @return
   *   True if the identifier is rate limited. False otherwise.
   */
  def rateLimited(identifier: String): ZIO[Any, RedisError, Boolean]
}

object RedisService {
  def rateLimited(identifier: String) = ZIO.serviceWithZIO[RedisService](_.rateLimited(identifier))

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

  def layer = ZLayer.fromZIO {
    for {
      redis     <- ZIO.service[Reloadable[Redis]]
      config    <- ZIO.service[RateLimitConfig]
      semaphore <- Semaphore.make(1)
    } yield RedisServiceLive(redis, config, semaphore)
  }

  def codecLayer = ZLayer.succeed[CodecSupplier](new CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec
  })

}

final case class RedisServiceLive(redisRef: Reloadable[Redis], config: RateLimitConfig, semaphore: Semaphore)
    extends RedisService {
  val layer = ZLayer.succeed(redisRef)

  case class RedisTimeoutError(detail: String) extends Throwable(s"Redis timeout $detail")

  def cacheOrExecute[T: Schema, E <: Throwable, R](key: String, expiration: Duration)(
      retrieveFresh: => ZIO[R, E, T]): ZIO[R, Throwable, T] =
    for {
      redis      <- redisRef.get
      maybeValue <- redis.get(key).returning[T].timeoutTo(None)(identity)(1.seconds)
      value      <- maybeValue match {
                      case Some(value) => ZIO.succeed(value)
                      case None        =>
                        for {
                          value <- retrieveFresh
                          _     <-
                            redis
                              .set(key, value, Some(expiration))
                              .timeoutFail(RedisTimeoutError("set"))(1.seconds)
                              .tapError { e =>
                                ZIO.logError(s"Failed to set value in Redis. ${e.toString} $key") *>
                                  resetRedisConnection
                              }
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
          redis                   <- redisRef.get
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
                                             ZIO.logError(s"Failed to set values in Redis. ${e.toString}.")
                                               *> resetRedisConnection
                                           }.forkDaemon
        } yield retrieved.values.toList ++ values.flatten

  override def rateLimited(identifier: String) = {
    val lua = """
      local currentKey = KEYS[1] -- identifier including prefixes
      local previousKey = KEYS[2] -- key of the previous bucket
      local tokens = tonumber(ARGV[1]) -- tokens per window
      local now = ARGV[2] -- current timestamp in milliseconds
      local window = ARGV[3] -- interval in milliseconds

      local requestsInCurrentWindow = redis.call("GET", currentKey)
      if requestsInCurrentWindow == false then
        requestsInCurrentWindow = -1
      end

      local requestsInPreviousWindow = redis.call("GET", previousKey)
      if requestsInPreviousWindow == false then
        requestsInPreviousWindow = 0
      end
      local percentageInCurrent = (now % window) / window
      if requestsInPreviousWindow * (1 - percentageInCurrent) + requestsInCurrentWindow >= tokens then
        return -1
      end

      local newValue = redis.call("INCR", currentKey)
      if newValue == 1 then
        -- The first time this key is set, the value will be 1.
        -- So we only need the expire command once
        redis.call("PEXPIRE", currentKey, window * 2 + 1000) -- Enough time to overlap with a new window + 1 second
      end
      return tokens - newValue
                |""".stripMargin

    import zio.redis.Input.*
    import zio.redis.Output.*

    val window = config.timeWindow
    val tokens = config.maxRequests

    for {
      now           <- Clock.instant.map(_.toEpochMilli)
      windowSize     = window.toMillis
      currentWindow  = Math.floor(now / windowSize)
      currentKey     = s"$identifier:$currentWindow"
      previousWindow = currentWindow - windowSize
      previousKey    = s"$identifier:$previousWindow"
      redis         <- redisRef.get
      remaining     <- redis
                         .eval(lua, Chunk(currentKey, previousKey), Chunk(tokens.toLong, now, windowSize))(
                           StringInput,
                           LongInput).returning[Long](LongOutput).tapError { _ =>
                           ZIO.logError(s"Rate Limit check failed for $identifier") *> resetRedisConnection.ignore
                         }
      _             <- ZIO.logDebug(s"Remaining requests for $identifier: $remaining")
    } yield remaining <= 0
  }

  private def resetRedisConnection = ZIO.whenZIO(semaphore.available.map(_ > 0)) {
    semaphore.withPermit {
      for {
        _ <- ZIO.logInfo("Resetting Redis connection")
        _ <- redisRef.reload
      } yield ()
    }
  }
}
