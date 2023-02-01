package muse.service

import com.stuart.zcaffeine
import zio.cache
import zio.metrics.Metric

object CacheUtils {
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

  case class ZioCacheStatReporter[Key, Error, Value](name: String, zcache: cache.Cache[Key, Error, Value]) {
    val hits     = Metric.gauge("cache_hits").tagged("cache_name", name)
    val misses   = Metric.gauge("cache_misses").tagged("cache_name", name)
    val requests = Metric.gauge("cache_request_count").tagged("cache_name", name)

    def report = for {
      stats <- zcache.cacheStats
      _     <- hits.set(stats.hits)
      _     <- misses.set(stats.misses)
      _     <- requests.set(stats.size)
    } yield ()
  }
}
