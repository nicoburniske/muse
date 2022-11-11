package muse.service.spotify

import com.stuart.zcaffeine
import com.stuart.zcaffeine.ZCaffeine
import com.stuart.zcaffeine.ZCaffeine.State
import com.stuart.zcaffeine.types.*
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
  val layer            = (albumCacheLayer ++ artistCacheLayer ++ userCacheLayer).tap(env =>
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

  def playlistCache(spotify: SpotifyAPI[Task]) = for {
    cache   <- Cache.make(
                 1000,
                 5.minutes,
                 Lookup((input: PlaylistInput) => spotify.getPlaylist(input.playlistId, input.market, input.fields))
               )
    reporter = CacheUtils.ZioCacheStatReporter("playlist", cache)
    _       <- reporter.report.repeat(cacheStatReportInterval).forkDaemon
  } yield cache
}

case class PlaylistInput(playlistId: String, fields: Option[String], market: Option[String])
