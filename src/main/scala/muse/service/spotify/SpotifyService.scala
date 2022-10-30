package muse.service.spotify

import com.stuart.zcaffeine
import muse.config.SpotifyConfig
import muse.domain.common.EntityType
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.{RequestSession, UserSessions}
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import zio.cache.{Cache, Lookup}
import zio.{Schedule, Task, ZEnvironment, ZIO, ZLayer, durationInt}

trait SpotifyService {
  def getCurrentUserProfile: Task[User]
  def getTrackRecommendations(input: TrackRecsInput): Task[Vector[Track]]
  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): Task[SearchResult]
  def getUserProfile(userId: String): Task[User]
  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean]
  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): Task[UserPlaylist]
  def getTrack(id: String, market: Option[String] = None): Task[Track]
  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]]
  def getTrackAudioFeatures(id: String): Task[AudioFeatures]
  def getTracksAudioFeatures(ids: Vector[String]): Task[Vector[AudioFeatures]]
  def getArtist(id: String): Task[Artist]
  def getArtists(ids: Seq[String]): Task[Vector[Artist]]
  def getAlbum(id: String): Task[Album]
  def getAlbums(ids: Seq[String]): Task[Vector[Album]]
  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): Task[Paging[UserPlaylist]]
  def getAllUserPlaylists(userId: String): Task[Vector[UserPlaylist]]
  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None): Task[Paging[PlaylistTrack]]
  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]]
  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): Task[Paging[Track]]
  def getAllAlbumTracks(albumId: String): Task[Vector[Track]]
  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None): Task[Paging[Album]]
  def getAllArtistAlbums(artistId: String): Task[Vector[Album]]
  def getArtistTopTracks(artistId: String, country: String = "US"): Task[Vector[Track]]
  def checkUserSavedTracks(trackIds: Vector[String]): Task[Vector[(String, Boolean)]]
  def saveTracks(trackIds: Vector[String]): Task[Boolean]
  def removeSavedTracks(trackIds: Vector[String]): Task[Boolean]
  def getAvailableDevices: Task[Vector[PlaybackDevice]]
  def transferPlayback(deviceId: String): Task[Boolean]
  def startPlayback(device: Option[String], startPlaybackBody: Option[StartPlaybackBody]): Task[Boolean]
  def currentPlaybackState: Task[Option[PlaybackState]]
  def seekPlayback(deviceId: Option[String], positionMs: Int): Task[Boolean]
  def pausePlayback(deviceId: Option[String]): Task[Boolean]
  def skipToNext(deviceId: Option[String]): Task[Boolean]
  def skipToPrevious(deviceId: Option[String]): Task[Boolean]
  def toggleShuffle(shuffleState: Boolean): Task[Boolean]
}

object SpotifyService {

  def live(accessToken: String) = for {
    backend       <- ZIO.service[SttpBackend[Task, Any]]
    spotify        = SpotifyAPI(backend, accessToken)
    // Given permissions vary, we want to create cache per instance to avoid conflicts.
    likeCache     <- SpotifyCache.savedSongsCache
    playlistCache <- Cache.make(
                       1000,
                       5.minutes,
                       Lookup((input: PlaylistInput) => spotify.getPlaylist(input.playlistId, input.market, input.fields))
                     )
    // These are global caches that we can share them across instances.
    artistCache   <- ZIO.service[zcaffeine.Cache[Any, String, Artist]]
    albumCache    <- ZIO.service[zcaffeine.Cache[Any, String, Album]]
  } yield SpotifyServiceLive(spotify, likeCache, playlistCache, artistCache, albumCache)

  def getCurrentUserProfile = ZIO.serviceWithZIO[SpotifyService](_.getCurrentUserProfile)

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.search(query, entityTypes, limit, offset))

  def getUserProfile(userId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getUserProfile(userId))

  def isValidEntity(entityId: String, entityType: EntityType) =
    ZIO.serviceWithZIO[SpotifyService](_.isValidEntity(entityId, entityType))

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getPlaylist(playlistId, fields, market))

  def getTrack(id: String, market: Option[String] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getTrack(id, market))

  def getTracks(ids: Seq[String], market: Option[String] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getTracks(ids, market))

  def getTrackAudioFeatures(id: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getTrackAudioFeatures(id))

  def getTracksAudioFeatures(ids: Vector[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.getTracksAudioFeatures(ids))

  def getArtist(id: String) = ZIO.serviceWithZIO[SpotifyService](_.getArtist(id))

  def getArtists(ids: Seq[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.getArtists(ids))

  def getAlbum(id: String) = ZIO.serviceWithZIO[SpotifyService](_.getAlbum(id))

  def getAlbums(ids: Seq[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.getAlbums(ids))

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getUserPlaylists(userId, limit, offset))

  def getAllUserPlaylists(userId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllUserPlaylists(userId))

  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getSomePlaylistTracks(playlistId, limit, offset))

  def getAllPlaylistTracks(playlistId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllPlaylistTracks(playlistId))

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getSomeAlbumTracks(album, limit, offset))

  def getAllAlbumTracks(albumId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllAlbumTracks(albumId))

  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getSomeArtistAlbums(artistId, limit, offset))

  def getAllArtistAlbums(artistId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllArtistAlbums(artistId))

  def getArtistTopTracks(artistId: String, country: String = "US") =
    ZIO.serviceWithZIO[SpotifyService](_.getArtistTopTracks(artistId, country))

  def checkUserSavedTracks(trackIds: Vector[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.checkUserSavedTracks(trackIds))

  def startPlayback(device: Option[String], startPlaybackBody: Option[StartPlaybackBody]) =
    ZIO.serviceWithZIO[SpotifyService](_.startPlayback(device, startPlaybackBody))

  def getAvailableDevices =
    ZIO.serviceWithZIO[SpotifyService](_.getAvailableDevices)

  def transferPlayback(deviceId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.transferPlayback(deviceId))

  def saveTracks(trackIds: Vector[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.saveTracks(trackIds))

  def currentPlaybackState =
    ZIO.serviceWithZIO[SpotifyService](_.currentPlaybackState)

  def seekPlayback(deviceId: Option[String], positionMs: Int) =
    ZIO.serviceWithZIO[SpotifyService](_.seekPlayback(deviceId, positionMs))
}

case class PlaylistInput(playlistId: String, fields: Option[String], market: Option[String])

case class SpotifyServiceLive(
    s: SpotifyAPI[Task],
    likeCache: zcaffeine.Cache[Any, String, Boolean],
    playlistCache: Cache[PlaylistInput, Throwable, UserPlaylist],
    artistCache: zcaffeine.Cache[Any, String, Artist],
    albumCache: zcaffeine.Cache[Any, String, Album]
) extends SpotifyService {
  def getCurrentUserProfile = s.getCurrentUserProfile
  def getTrackRecommendations(input: TrackRecsInput) = s.getTrackRecommendations(input).map(_.tracks)

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None) =
    s.search(query, entityTypes, limit, offset)

  def getUserProfile(userId: String) =
    s.getUserProfile(userId)

  def isValidEntity(entityId: String, entityType: EntityType) =
    s.isValidEntity(entityId, entityType)

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None) =
    playlistCache.get(PlaylistInput(playlistId, fields, market))
      <* playlistCache.cacheStats.flatMap { stats => ZIO.logInfo(s"GetPlaylist stats: $stats") }

  def getTrack(id: String, market: Option[String] = None) =
    s.getTrack(id, market)

  def getTracks(ids: Seq[String], market: Option[String] = None) =
    s.getTracks(ids.toVector, market)

  def getTracksAudioFeatures(ids: Vector[String]) =
    s.getTracksAudioFeatures(ids)

  def getTrackAudioFeatures(id: String) =
    s.getTrackAudioFeatures(id)

  def getArtist(id: String): Task[Artist] = artistCache.get(id)(s.getArtist)

  def getArtists(ids: Seq[String]): Task[Vector[Artist]] =
    artistCache
      .getAll(ids.toSet) { ids =>
        for {
          artists <- s.getArtists(ids.toVector)
        } yield artists.map(a => a.id -> a).toMap
      }.map(_.values.toVector)

  def getAlbum(id: String): Task[Album] = albumCache.get(id)(s.getAlbum)

  def getAlbums(ids: Seq[String]): Task[Vector[Album]] =
    albumCache
      .getAll(ids.toSet) { ids =>
        for {
          albums <- s.getAlbums(ids.toVector)
        } yield albums.map(a => a.id -> a).toMap
      }.map(_.values.toVector)

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): Task[Paging[UserPlaylist]] =
    s.getUserPlaylists(userId, limit, offset)

  def getAllUserPlaylists(userId: String): Task[Vector[UserPlaylist]] =
    s.getAllUserPlaylists(userId)

  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None): Task[Paging[PlaylistTrack]] =
    s.getSomePlaylistTracks(playlistId, limit, offset)

  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]] =
    s.getAllPlaylistTracks(playlistId)

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): Task[Paging[Track]] =
    s.getSomeAlbumTracks(album, limit, offset)

  def getAllAlbumTracks(albumId: String): Task[Vector[Track]] =
    s.getAllAlbumTracks(albumId)

  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None): Task[Paging[Album]] =
    s.getSomeArtistAlbums(artistId, limit, offset)

  def getAllArtistAlbums(artistId: String): Task[Vector[Album]] = s.getAllArtistAlbums(artistId)

  def getArtistTopTracks(artistId: String, country: String = "US"): Task[Vector[Track]] =
    s.getArtistTopTracks(artistId, country)

  def checkUserSavedTracks(trackIds: Vector[String]): Task[Vector[(String, Boolean)]] =
    likeCache
      .getAll(trackIds.toSet) { ids =>
        for {
          likes <- s.checkUserSavedTracks(ids.toVector)
        } yield likes.toMap
      }.map(_.toVector)

  def startPlayback(device: Option[String], startPlaybackBody: Option[StartPlaybackBody]) =
    s.startPlayback(device, startPlaybackBody)

  def getAvailableDevices                                     = s.getAvailableDevices
  def transferPlayback(deviceId: String)                      = s.transferPlayback(deviceId)
  def seekPlayback(deviceId: Option[String], positionMs: Int) = s.seekPlayback(deviceId, positionMs)
  def saveTracks(trackIds: Vector[String])                    = s.saveTracks(trackIds)
  def removeSavedTracks(trackIds: Vector[String])             = s.removeSavedTracks(trackIds)
  def currentPlaybackState                                    = s.getPlaybackState
  def pausePlayback(deviceId: Option[String])                 = s.pausePlayback(deviceId)
  def skipToNext(deviceId: Option[String])                    = s.skipToNext(deviceId)
  def skipToPrevious(deviceId: Option[String])                = s.skipToPrevious(deviceId)
  def toggleShuffle(shuffleState: Boolean)                    = s.toggleShuffle(shuffleState)
}
