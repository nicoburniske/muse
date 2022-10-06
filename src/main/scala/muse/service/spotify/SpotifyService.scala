package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.common.EntityType
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.{RequestSession, UserSessions}
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import zio.{Schedule, Task, ZIO, ZLayer}

trait SpotifyService {
  def getCurrentUserProfile: Task[User]
  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): Task[SearchResult]
  def getUserProfile(userId: String): Task[User]
  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean]
  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): Task[UserPlaylist]
  def getTrack(id: String, market: Option[String] = None): Task[Track]
  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]]
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
  def startPlayback(device: Option[String], startPlaybackBody: StartPlaybackBody): Task[Boolean]
  def getAvailableDevices: Task[Vector[PlaybackDevice]]
  def transferPlayback(deviceId: String): Task[Boolean]
  def saveTracks(trackIds: Vector[String]): Task[Boolean]
  def currentPlaybackState: Task[Option[PlaybackState]]
}

object SpotifyService {
  val live = for {
    accessToken <- RequestSession.get[UserSession].map(_.accessToken)
    backend     <- ZIO.service[SttpBackend[Task, Any]]
    spotify      = SpotifyAPI(backend, accessToken)
  } yield SpotifyServiceLive(spotify)

  val layer    = ZLayer.fromZIO(live)
  val getLayer = live.map(ZLayer.succeed(_))

  def live(accessToken: String) = for {
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyServiceLive(SpotifyAPI(backend, accessToken))

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

  def startPlayback(device: Option[String], startPlaybackBody: StartPlaybackBody) =
    ZIO.serviceWithZIO[SpotifyService](_.startPlayback(device, startPlaybackBody))

  def getAvailableDevices =
    ZIO.serviceWithZIO[SpotifyService](_.getAvailableDevices)

  def transferPlayback(deviceId: String) =
    ZIO.serviceWithZIO[SpotifyService](_.transferPlayback(deviceId))

  def saveTracks(trackIds: Vector[String]) =
    ZIO.serviceWithZIO[SpotifyService](_.saveTracks(trackIds))

  def currentPlaybackState =
    ZIO.serviceWithZIO[SpotifyService](_.currentPlaybackState)
}

case class SpotifyServiceLive(s: SpotifyAPI[Task]) extends SpotifyService {
  def getCurrentUserProfile = s.getCurrentUserProfile

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None) =
    s.search(query, entityTypes, limit, offset)

  def getUserProfile(userId: String) =
    s.getUserProfile(userId)

  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean] =
    s.isValidEntity(entityId, entityType)

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): Task[UserPlaylist] =
    s.getPlaylist(playlistId, fields, market)

  def getTrack(id: String, market: Option[String] = None): Task[Track] =
    s.getTrack(id, market)

  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]] =
    s.getTracks(ids, market)

  def getArtist(id: String): Task[Artist] = s.getArtist(id)

  def getArtists(ids: Seq[String]): Task[Vector[Artist]] =
    s.getArtists(ids)

  def getAlbum(id: String): Task[Album] = s.getAlbum(id)

  def getAlbums(ids: Seq[String]): Task[Vector[Album]] =
    s.getAlbums(ids)

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
    s.checkUserSavedTracks(trackIds)

  def startPlayback(device: Option[String], startPlaybackBody: StartPlaybackBody) =
    s.startPlayback(device, startPlaybackBody)

  def getAvailableDevices                  = s.getAvailableDevices
  def transferPlayback(deviceId: String)   = s.transferPlayback(deviceId)
  def saveTracks(trackIds: Vector[String]) = s.saveTracks(trackIds)
  def currentPlaybackState                 = s.getPlaybackState
}
