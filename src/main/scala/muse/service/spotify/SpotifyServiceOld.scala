package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.common.EntityType
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.domain.response.{DetailedAlbum, DetailedArtist, DetailedPlaylist, DetailedTrack}
import muse.service.UserSessions
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.StatusCode
import zio.{Schedule, Task, ZIO, ZLayer}

object SpotifyServiceOld {
  val live = for {
    user    <- ZIO.service[UserSession]
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, user.accessToken)

  def live(accessToken: String) = for {
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, accessToken)

  def getDetailedPlaylist(accessToken: String, playlistId: String) = for {
    s                 <- live(accessToken)
    res               <- s.getPlaylist(playlistId) <&> s.getAllPlaylistTracks(playlistId)
    (playlist, tracks) = res
  } yield DetailedPlaylist(
    playlist.collaborative,
    playlist.description,
    playlist.externalUrls,
    playlist.id,
    playlist.images.map(_.url),
    playlist.name,
    playlist.owner,
    playlist.primaryColor,
    playlist.public,
    tracks.toList,
    EntityType.Playlist
  )

  def getDetailedAlbum(accessToken: String, albumId: String) = for {
    s              <- live(accessToken)
    res            <- s.getAlbums(List(albumId)).map(_.head) <&> s.getAllAlbumTracks(albumId)
    (album, tracks) = res
  } yield DetailedAlbum(
    album.albumGroup,
    album.albumType,
    album.artists,
    album.externalUrls,
    album.genres.getOrElse(Nil),
    album.id,
    album.images.map(_.url),
    album.label,
    album.name,
    album.popularity,
    album.releaseDate,
    tracks.toList,
    EntityType.Album
  )

  def getDetailedArtist(accessToken: String, artistId: String) = for {
    s                       <- live(accessToken)
    res                     <- s.getArtist(artistId) <&>
                                 s.getSomeArtistAlbums(artistId) <&>
                                 s.getArtistTopTracks(artistId)
    (artist, albums, tracks) = res
  } yield DetailedArtist(
    artist.externalUrls,
    artist.followers.get.total,
    artist.genres.get,
    artist.href,
    artist.id,
    artist.images.fold(Nil)(_.map(_.url)),
    artist.name,
    artist.popularity.get,
    EntityType.Artist,
    albums.items.toList,
    tracks.toList
  )

  def getDetailedTrack(accessToken: String, trackId: String) = for {
    s   <- live(accessToken)
    res <- s.getTracks(List(trackId)).map(_.head)
  } yield DetailedTrack(
    res.album.get,
    res.artists,
    res.discNumber,
    res.durationMs,
    res.explicit,
    res.externalUrls,
    res.id,
    res.isPlayable.getOrElse(false),
    res.name,
    res.popularity.get,
    res.previewUrl.get,
    res.trackNumber,
    res.isLocal
  )

  def getAlbumsPar(accessToken: String, ids: Seq[String]) = for {
    spotify <- live(accessToken)
    res     <- parallelRequest(ids, 20, spotify.getAlbums)
  } yield res

  def getArtistsPar(accessToken: String, ids: Seq[String]) = for {
    spotify <- live(accessToken)
    res     <- parallelRequest(ids, 50, spotify.getArtists)
  } yield res

  // This sucks. Might need to cache this.
  // Is different from the others because you can only get one playlist at a time.
  def getPlaylistsPar(accessToken: String, ids: Seq[String]) = live(accessToken).flatMap { spotify =>
    ZIO.foreachPar(ids.toVector)(id => spotify.getPlaylist(id))
  }

  def getTracksPar(accessToken: String, ids: Seq[String]) =
    for {
      spotify <- live(accessToken)
      res     <- parallelRequest(ids, 50, spotify.getTracks(_))
    } yield res

  def parallelRequest[I, R](
      ids: Seq[I],
      maxPerRequest: Int,
      singleRequest: Seq[I] => Task[Vector[R]]): ZIO[Any, Throwable, Vector[R]] = for {
    responses <- ZIO.foreachPar(ids.grouped(maxPerRequest).toVector)(singleRequest)
  } yield responses.flatten
}

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
