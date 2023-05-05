package muse.service.spotify

import com.stuart.zcaffeine
import muse.config.SpotifyConfig
import muse.domain.common.EntityType
import muse.domain.common.Types.UserId
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.cache.{RedisService, RedisServiceLive}
import muse.service.{RequestSession, UserSessionService}
import muse.utils.Givens
import muse.utils.Givens.given
import muse.utils.Utils.addTimeLog
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import zio.cache.{Cache, Lookup}
import zio.redis.Redis
import zio.{Clock, Ref, Task, ZEnvironment, ZIO, ZLayer, durationInt}

trait SpotifyService {
  def getCurrentUserProfile: Task[PrivateUser]
  def getTrackRecommendations(input: TrackRecsInput): Task[Vector[Track]]
  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): Task[SearchResult]
  def getUserProfile(userId: UserId): Task[PublicUser]
  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean]
  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): Task[SinglePlaylist]
  def getTrack(id: String, market: Option[String] = None): Task[Track]
  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]]
  def getTrackAudioAnalysis(id: String): Task[AudioAnalysis]
  def getTrackAudioFeatures(id: String): Task[AudioFeatures]
  def getTracksAudioFeatures(ids: Vector[String]): Task[Vector[AudioFeatures]]
  def getArtist(id: String): Task[Artist]
  def getArtists(ids: Seq[String]): Task[Vector[Artist]]
  def getAlbum(id: String): Task[Album]
  def getAlbums(ids: Seq[String]): Task[Vector[Album]]
  def getUserPlaylists(userId: UserId, limit: Int, offset: Option[Int] = None): Task[Paging[BulkPlaylist]]
  def getCurrentUserPlaylists(limit: Int, offset: Option[Int] = None): Task[Paging[BulkPlaylist]]
  def getAllUserPlaylists(userId: UserId): Task[Vector[BulkPlaylist]]
  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None): Task[Paging[PlaylistTrack]]
  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]]
  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): Task[Paging[SimpleTrack]]
  def getAllAlbumTracks(albumId: String): Task[Vector[SimpleTrack]]
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
  
  type Env = SttpBackend[Task, Any] & RedisService  & Ref[Option[Long]] 

  def live(accessToken: String) = for {
    backend    <- ZIO.service[SttpBackend[Task, Any]]
    // RetryAfter time in seconds.
    retryAfter <- ZIO.service[Ref[Option[Long]]]
    asLibRef    = Givens.zioRef(retryAfter)
    spotify     = SpotifyAPI(backend, asLibRef, accessToken)
    redis      <- ZIO.service[RedisService]
  } yield SpotifyServiceLive(spotify, redis)

  def getCurrentUserProfile = ZIO.serviceWithZIO[SpotifyService](_.getCurrentUserProfile)

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.search(query, entityTypes, limit, offset))

  def getUserProfile(userId: UserId) =
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

  def getUserPlaylists(userId: UserId, limit: Int, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getUserPlaylists(userId, limit, offset))

  def getAllUserPlaylists(userId: UserId) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllUserPlaylists(userId))

  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getSomePlaylistTracks(playlistId, limit, offset))

  def getAllPlaylistTracks(playlistId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.getAllPlaylistTracks(playlistId))

  def getCurrentUserPlaylists(limit: Int, offset: Option[Int] = None) =
    ZIO.serviceWithZIO[SpotifyService](_.getCurrentUserPlaylists(limit, offset))

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

case class SpotifyServiceLive(
    s: SpotifyAPI[Task],
    redisService: RedisService
) extends SpotifyService {

  import zio.durationInt
  import zio.schema.{DeriveSchema, Schema}

  given Schema[SinglePlaylist] = DeriveSchema.gen[SinglePlaylist]
  given Schema[Artist]         = DeriveSchema.gen[Artist]
  given Schema[Track]          = DeriveSchema.gen[Track]
  given Schema[Album]          = DeriveSchema.gen[Album]
  given Schema[PublicUser]     = DeriveSchema.gen[PublicUser]

  def getCurrentUserProfile                          = s.getCurrentUserProfile
  def getTrackRecommendations(input: TrackRecsInput) = s.getTrackRecommendations(input).map(_.tracks)

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None) =
    s.search(query, entityTypes, limit, offset)

  def getUserProfile(userId: UserId) =
    redisService.cacheOrExecute("user:" + userId, 10.minutes)(s.getUserProfile(userId))

  def isValidEntity(entityId: String, entityType: EntityType) =
    entityType match
      case EntityType.Album    => getAlbum(entityId).isSuccess
      case EntityType.Artist   => getArtist(entityId).isSuccess
      case EntityType.Playlist => getPlaylist(entityId).isSuccess
      case EntityType.Track    => getTrack(entityId).isSuccess

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None) =
    redisService.cacheOrExecute("playlist:" + playlistId, 10.minutes)(s.getPlaylist(playlistId, fields, market))

  def trackKey(id: String)                                = "track:" + id
  def getTrack(id: String, market: Option[String] = None) =
    redisService.cacheOrExecute(trackKey(id), 30.minutes)(s.getTrack(id))

  def getTracks(ids: Seq[String], market: Option[String] = None) =
    redisService
      .cacheOrExecuteBulk(ids.toList, 1.hour)(trackKey) { ids =>
        for {
          tracks <- s.getTracks(ids.toVector)
        } yield tracks.map(a => a.id -> a).toMap
      }.map(_.toVector)

  def getTrackAudioAnalysis(id: String) =
    s.getTrackAudioAnalysis(id)

  def getTrackAudioFeatures(id: String) =
    s.getTrackAudioFeatures(id)

  def getTracksAudioFeatures(ids: Vector[String]) =
    s.getTracksAudioFeatures(ids)

  private def artistKey(id: String) = "artist:" + id

  def getArtist(id: String): Task[Artist] =
    redisService.cacheOrExecute(artistKey(id), 30.minutes)(s.getArtist(id))

  def getArtists(ids: Seq[String]): Task[Vector[Artist]] =
    redisService
      .cacheOrExecuteBulk(ids.toList, 1.hour)(artistKey) { ids =>
        for {
          artists <- s.getArtists(ids.toVector)
        } yield artists.map(a => a.id -> a).toMap
      }.map(_.toVector)

  private def albumKey(id: String) = "album:" + id

  def getAlbum(id: String): Task[Album] =
    redisService.cacheOrExecute(albumKey(id), 2.hours)(s.getAlbum(id))

  def getAlbums(ids: Seq[String]): Task[Vector[Album]] =
    redisService
      .cacheOrExecuteBulk(ids.toList, 1.hour)(albumKey) { ids =>
        for {
          albums <- s.getAlbums(ids.toVector)
        } yield albums.map(a => a.id -> a).toMap
      }.map(_.toVector)

  def getUserPlaylists(userId: UserId, limit: Int, offset: Option[Int] = None) =
    s.getUserPlaylists(userId, limit, offset)

  def getAllUserPlaylists(userId: UserId): Task[Vector[BulkPlaylist]] =
    s.getAllUserPlaylists(userId)

  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None) =
    s.getSomePlaylistTracks(playlistId, limit, offset)

  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]] =
    s.getAllPlaylistTracks(playlistId)

  def getCurrentUserPlaylists(limit: Int, offset: Option[Int] = None) =
    s.getCurrentUserPlaylists(limit, offset)

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None) =
    s.getSomeAlbumTracks(album, limit, offset)

  def getAllAlbumTracks(albumId: String) =
    s.getAllAlbumTracks(albumId)

  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None) =
    s.getSomeArtistAlbums(artistId, limit, offset)

  def getAllArtistAlbums(artistId: String): Task[Vector[Album]] = s.getAllArtistAlbums(artistId)

  def getArtistTopTracks(artistId: String, country: String = "US"): Task[Vector[Track]] =
    s.getArtistTopTracks(artistId, country)

  def checkUserSavedTracks(trackIds: Vector[String]): Task[Vector[(String, Boolean)]] =
    s.checkUserSavedTracks(trackIds)

  def startPlayback(device: Option[String], startPlaybackBody: Option[StartPlaybackBody]) =
    s.startPlayback(device, startPlaybackBody)

  def getAvailableDevices                                     = s.getAvailableDevices
  def transferPlayback(deviceId: String)                      = s.transferPlayback(deviceId)
  def seekPlayback(deviceId: Option[String], positionMs: Int) = s.seekPlayback(deviceId, positionMs)

  def saveTracks(trackIds: Vector[String])        = s.saveTracks(trackIds)
  def removeSavedTracks(trackIds: Vector[String]) = s.removeSavedTracks(trackIds)

  def currentPlaybackState                     = s.getPlaybackState
  def pausePlayback(deviceId: Option[String])  = s.pausePlayback(deviceId)
  def skipToNext(deviceId: Option[String])     = s.skipToNext(deviceId)
  def skipToPrevious(deviceId: Option[String]) = s.skipToPrevious(deviceId)
  def toggleShuffle(shuffleState: Boolean)     = s.toggleShuffle(shuffleState)
}
