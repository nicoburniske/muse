package muse.service.spotify

import com.stuart.zcaffeine
import com.stuart.zcaffeine.ZCaffeine
import com.stuart.zcaffeine.ZCaffeine.State
import com.stuart.zcaffeine.types.*
import muse.config.SpotifyServiceConfig
import muse.domain.spotify.{Album, Artist, User}
import muse.service.CacheUtils
import zio.cache.{Cache, Lookup}
import zio.metrics.prometheus.Registry
import zio.metrics.{Label, Metric}
import zio.{Duration, Schedule, Task, ZIO, ZLayer, durationInt}

object SpotifyCache {
  val cacheStatReportInterval = Schedule.spaced(10.seconds) && Schedule.forever

  val albumCacheLayer  = ZLayer.fromZIO(albumCache)
  val artistCacheLayer = ZLayer.fromZIO(artistCache)
  val userCacheLayer   = ZLayer.fromZIO(userCache)

  val layer = (albumCacheLayer ++ artistCacheLayer ++ userCacheLayer).tap(env =>
    val albumCache  = env.get[zcaffeine.Cache[Any, String, Album]]
    val artistCache = env.get[zcaffeine.Cache[Any, String, Artist]]
    val userCache   = env.get[zcaffeine.Cache[Any, String, User]]

    val cacheStats = List(
      "album"  -> albumCache,
      "artist" -> artistCache,
      "user"   -> userCache
    ).map { case (label, cache) => CacheUtils.CaffeineStatReporter(label, cache) }

    ZIO.foreachPar(cacheStats)(_.report).repeat(cacheStatReportInterval).forkDaemon
  )

  lazy val albumCache = for {
    cache     <- ZCaffeine[Any, String, Album]()
    config    <- ZIO.service[SpotifyServiceConfig]
    configured = configureCache(cache, 1.hour, config.albumCacheSize)
    built     <- configured.build()
  } yield built

  lazy val artistCache = for {
    cache     <- ZCaffeine[Any, String, Artist]()
    config    <- ZIO.service[SpotifyServiceConfig]
    configured = configureCache(cache, 1.hour, config.artistCacheSize)
    built     <- configured.build()
  } yield built

  lazy val userCache = for {
    cache     <- ZCaffeine[Any, String, User]()
    config    <- ZIO.service[SpotifyServiceConfig]
    configured = configureCache(cache, 1.hour, config.userCacheSize)
    built     <- configured.build()
  } yield built

  lazy val savedSongsCache = for {
    cache     <- ZCaffeine[Any, String, Boolean]()
    config    <- ZIO.service[SpotifyServiceConfig]
    configured = configureCache(cache, 1.hour, config.likedSongsCacheSize)
    built     <- configured.build()
  } yield built

  def configureCache[K, V](z: ZCaffeine[Any, State.Unconfigured, K, V], duration: Duration, maxSize: Long) = {
    z.initialCapacity(InitialCapacity(1))
      .maximumSize(MaxSize(1000))
      .enableScheduling()
      .recordStats()
      .expireAfterWrite(duration)
  }

  def playlistCache(spotify: SpotifyAPI[Task]) = for {
    config  <- ZIO.service[SpotifyServiceConfig]
    cache   <- Cache.make(
                 config.playlistCacheSize,
                 5.minutes,
                 Lookup((input: PlaylistInput) => spotify.getPlaylist(input.playlistId, input.market, input.fields))
               )
    reporter = CacheUtils.ZioCacheStatReporter("playlist", cache)
    _       <- reporter.report.repeat(cacheStatReportInterval).forkDaemon
  } yield cache
}

case class PlaylistInput(playlistId: String, fields: Option[String], market: Option[String])
