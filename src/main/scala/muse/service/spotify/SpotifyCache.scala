package muse.service.spotify

import com.stuart.zcaffeine
import com.stuart.zcaffeine.ZCaffeine
import com.stuart.zcaffeine.ZCaffeine.State
import com.stuart.zcaffeine.types.*
import muse.domain.spotify.{Album, Artist, User}
import zio.cache.{Cache, Lookup}
import zio.metrics.prometheus.Registry
import zio.metrics.{Label, Metric}
import zio.{Duration, Schedule, Task, ZIO, ZLayer, durationInt}

object SpotifyCache {
  val albumCacheLayer  = ZLayer.fromZIO(albumCache)
  val artistCacheLayer = ZLayer.fromZIO(artistCache)
  val userCacheLayer   = ZLayer.fromZIO(userCache)
  val layer            = (albumCacheLayer ++ artistCacheLayer ++ userCacheLayer).tap(env =>
    val albumCache  = env.get[zcaffeine.Cache[Any, String, Album]]
    val artistCache = env.get[zcaffeine.Cache[Any, String, Artist]]
    val userCache   = env.get[zcaffeine.Cache[Any, String, User]]

    val cacheStats = List(
      "album"  -> albumCache,
      "artist" -> artistCache,
      "user"   -> userCache
    ).map { case (label, cache) => CaffeineStatReporter(label, cache) }

    ZIO.foreachPar(cacheStats)(_.report).repeat(Schedule.spaced(10.seconds) && Schedule.forever).forkDaemon
  )

  case class CaffeineStatReporter[R, K, V](name: String, cache: zcaffeine.Cache[R, K, V]) {
    val hits      = Metric.gauge("cache_hits").tagged("cache_name", name)
    val misses    = Metric.gauge("cache_misses").tagged("cache_name", name)
    val requests  = Metric.gauge("cache_request_count").tagged("cache_name", name)
    val totalSize = Metric.gauge("cache_size").tagged("cache_name", name)

    def report = for {
      stats         <- cache.stats
      _             <- hits.set(stats.hitCount)
      _             <- misses.set(stats.missCount())
      _             <- requests.set(stats.requestCount())
      estimatedSize <- cache.estimatedSize
      _             <- totalSize.set(estimatedSize)
    } yield ()
  }

  lazy val albumCache = ZCaffeine[Any, String, Album]()
    .map(configureCache(1.hour, _))
    .flatMap(_.build())

  lazy val artistCache = ZCaffeine[Any, String, Artist]()
    .map(configureCache(1.hour, _))
    .flatMap(_.build())

  lazy val userCache = ZCaffeine[Any, String, User]()
    .map(configureCache(30.minutes, _))
    .flatMap(_.build())

  lazy val savedSongsCache = ZCaffeine[Any, String, Boolean]()
    .map(configureCache(10.seconds, _))
    .flatMap(_.build())

  def configureCache[K, V](duration: Duration, z: ZCaffeine[Any, State.Unconfigured, K, V]) = {
    z.initialCapacity(InitialCapacity(1))
      .maximumSize(MaxSize(1000))
      .enableScheduling()
      .recordStats()
      .expireAfterWrite(duration)
  }
}
