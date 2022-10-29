package muse.service.spotify

import com.stuart.zcaffeine.ZCaffeine
import com.stuart.zcaffeine.ZCaffeine.State
import muse.domain.spotify.Album
import com.stuart.zcaffeine.types.*
import com.stuart.zcaffeine.ZCaffeine
import muse.domain.spotify.{Album, Artist}
import zio.{Duration, ZLayer, durationInt}

object SpotifyCache {
  val albumCacheLayer  = ZLayer.fromZIO(albumCache.flatMap(_.build()))
  val artistCacheLayer = ZLayer.fromZIO(artistCache.flatMap(_.build()))
  val layer            = albumCacheLayer ++ artistCacheLayer

  lazy val albumCache = ZCaffeine[Any, String, Album]().map(configureCache(1.hour, _))

  lazy val artistCache = ZCaffeine[Any, String, Artist]().map(configureCache(1.hour, _))

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
