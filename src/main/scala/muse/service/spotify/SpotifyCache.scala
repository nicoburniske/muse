package muse.service.spotify

import com.stuart.zcaffeine.ZCaffeine
import com.stuart.zcaffeine.ZCaffeine.State
import com.stuart.zcaffeine.types.*
import muse.domain.spotify.{Album, Artist, User}
import zio.cache.{Cache, Lookup}
import zio.{Duration, Task, ZIO, ZLayer, durationInt}

object SpotifyCache {
  val albumCacheLayer  = ZLayer.fromZIO(albumCache)
  val artistCacheLayer = ZLayer.fromZIO(artistCache)
  val userCacheLayer   = ZLayer.fromZIO(userCache)
  val layer            = albumCacheLayer ++ artistCacheLayer ++ userCacheLayer

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
