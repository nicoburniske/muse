package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.common.EntityType
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.UserSessions
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.StatusCode
import zio.{Schedule, Task, ZIO, ZLayer}

trait SpotifyService {
  def getCurrentUserProfile: Task[User]
  def getUserProfile(userId: String): Task[User]
  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean]
  def getPlaylist(
      playlistId: String,
      fields: Option[String] = None,
      market: Option[String] = None): Task[UserPlaylist]
  def getTrack(id: String, market: Option[String] = None): Task[Track]
  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]]
  def getArtist(id: String): Task[Artist]
  def getArtists(ids: Seq[String]): Task[Vector[Artist]]
  def getAlbum(id: String): Task[Album]
  def getAlbums(ids: Seq[String]): Task[Vector[Album]]
  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): Task[Paging[UserPlaylist]]
  def getAllUserPlaylists(userId: String): Task[Vector[UserPlaylist]]
  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): Task[Paging[PlaylistTrack]]
  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]]
  def getSomeAlbumTracks(
      album: String,
      limit: Option[Int] = None,
      offset: Option[Int] = None): Task[Paging[Track]]
  def getAllAlbumTracks(albumId: String): Task[Vector[Track]]
  def getSomeArtistAlbums(
      artistId: String,
      limit: Option[Int] = None,
      offset: Option[Int] = None): Task[Paging[Album]]
  def getAllArtistAlbums(artistId: String): Task[Vector[Album]]
  def getArtistTopTracks(artistId: String, country: String = "US"): Task[Vector[Track]]
}

object SpotifyService {
  val live = for { api <- ZIO.service[SpotifyAPI[Task]] } yield SpotifyServiceImpl(api)

  def live(accessToken: String) = for {
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyServiceImpl(SpotifyAPI(backend, accessToken))

  val layer = ZLayer.fromZIO(live)

  def getCurrentUserProfile = ZIO.serviceWithZIO[SpotifyService](_.getCurrentUserProfile)

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
}

case class SpotifyServiceImpl(s: SpotifyAPI[Task]) extends SpotifyService {
  def getCurrentUserProfile: Task[User] = s.getCurrentUserProfile

  def getUserProfile(userId: String): Task[User] =
    s.getUserProfile(userId)

  def isValidEntity(entityId: String, entityType: EntityType): Task[Boolean] =
    s.isValidEntity(entityId, entityType)

  def getPlaylist(
      playlistId: String,
      fields: Option[String] = None,
      market: Option[String] = None): Task[UserPlaylist] =
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

  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): Task[Paging[PlaylistTrack]] =
    s.getSomePlaylistTracks(playlistId, limit, offset)

  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]] =
    s.getAllPlaylistTracks(playlistId)

  def getSomeAlbumTracks(
      album: String,
      limit: Option[Int] = None,
      offset: Option[Int] = None): Task[Paging[Track]] =
    s.getSomeAlbumTracks(album, limit, offset)

  def getAllAlbumTracks(albumId: String): Task[Vector[Track]] =
    s.getAllAlbumTracks(albumId)

  def getSomeArtistAlbums(
      artistId: String,
      limit: Option[Int] = None,
      offset: Option[Int] = None): Task[Paging[Album]] =
    s.getSomeArtistAlbums(artistId, limit, offset)

  def getAllArtistAlbums(artistId: String): Task[Vector[Album]] = s.getAllArtistAlbums(artistId)

  def getArtistTopTracks(artistId: String, country: String = "US"): Task[Vector[Track]] =
    s.getArtistTopTracks(artistId, country)
}
