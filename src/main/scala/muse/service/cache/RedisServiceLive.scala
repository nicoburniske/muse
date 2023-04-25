package muse.service.cache

import muse.config.{RateLimitConfig, RedisCacheConfig}
import zio.*
import zio.redis.*
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, JsonCodec, ProtobufCodec}

import scala.util.control.NoStackTrace

final case class RedisServiceLive(redisRef: Reloadable[Redis], config: RateLimitConfig, semaphore: Semaphore)
    extends RedisService {
  val timeoutDuration = 500.millis

  def get[K: Schema, V: Schema](key: K) =
    executeOrReset(_.get(key).returning[V])(s"Get $key")

  def set[K: Schema, V: Schema](key: K, value: V, expiration: Option[Duration]) =
    executeOrReset(_.set(key, value, expiration))(s"Set $key")

  override def delete[K: Schema](key: K) =
    executeOrReset(_.del(key).map(_ > 0))(s"Delete $key")

  def cacheOrExecute[K: Schema, T: Schema, E <: Throwable, R](key: K, expiration: Duration)(retrieveFresh: => ZIO[R, E, T]) =
    for {
      redis      <- redisRef.get
      maybeValue <- redis.get(key).returning[T].timeoutTo(None)(identity)(timeoutDuration)
      value      <- maybeValue match {
                      case Some(value) => ZIO.succeed(value)
                      case None        =>
                        for {
                          value <- retrieveFresh
                          _     <- executeOrReset(_.set(key, value, Some(expiration)))(s"Set $key").forkDaemon
                        } yield value
                    }
    } yield value

  def cacheOrExecuteBulk[K: Schema, V: Schema, E <: Throwable, R, ID](ids: List[ID], expiration: Duration)(idToKey: ID => K)(
      retrieveFresh: List[ID] => ZIO[R, E, Map[ID, V]]) =
    ids match
      case Nil => ZIO.succeed(Nil)
      case ids =>
        for {
          redis                   <- redisRef.get
          values: List[Option[V]] <- ZIO
                                       .foreachPar(ids.map(idToKey))(redis.get(_).returning[V])
                                       .timeoutTo(List.fill(ids.size)(None))(identity)(timeoutDuration)
          _                       <- ZIO.logError("Values retrieved are not the same size as ids").when(values.size != ids.size)
          zipped                   = ids zip values
          missing                  = zipped.filter(_._2.isEmpty).map(_._1)
          retrieved               <- if (missing.isEmpty) ZIO.succeed(Map.empty) else retrieveFresh(missing)
          // Set new values in redis.
          _                       <- retrieved.toList.map { case (id, v) => idToKey(id) -> v } match
                                       case Nil     => ZIO.unit
                                       case entries =>
                                         executeOrReset { redis =>
                                           ZIO
                                             .foreachPar(entries) { case (key, value) => redis.set(key, value, Some(expiration)) }
                                         }(s"MultiSet $ids").forkDaemon
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
      remaining     <- executeOrReset {
                         _.eval(lua, Chunk(currentKey, previousKey), Chunk(tokens.toLong, now, windowSize))(StringInput, LongInput)
                           .returning[Long](LongOutput)
                       }(s"Rate limit $identifier")
      _             <- ZIO.logDebug(s"Remaining requests for $identifier: $remaining")
    } yield remaining <= 0
  }

  private def executeOrReset[E, R](func: Redis => IO[E, R])(label: => String): IO[E | RedisTimeoutError, R] = for {
    redis                 <- redisRef.get
    maybeValue: Option[R] <- func(redis).timeout(timeoutDuration)
    // Reset connection on timeout.
    _                     <- maybeValue.fold {
                               ZIO.logError(s"Failed to execute Redis command $label") *> reloadRedis
                             }(_ => ZIO.unit).forkDaemon
    result                <- maybeValue match
                               case Some(value) => ZIO.succeed(value)
                               case None        => ZIO.fail(RedisTimeoutError(label))
  } yield result

  private def reloadRedis = semaphore.withPermit {
    val needsReload = redisRef.get.flatMap(_.ping()).timeout(50.millis).map(_.isEmpty)
    ZIO.whenZIO(needsReload) {
      ZIO.logInfo("Resetting Redis connection") *> redisRef
        .reload.foldCause(
          e => ZIO.logErrorCause(s"Failed to reload Redis connection ${e.toString}", e),
          _ => ZIO.logInfo("Successfully reloaded Redis connection"))
    }
  }
}
