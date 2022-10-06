package muse.service.spotify

import muse.domain.common.EntityType
import muse.domain.spotify.*
import muse.utils.MonadError
import sttp.client3.*
import sttp.client3.ziojson.*
import sttp.model.{Method, ResponseMetadata, StatusCode, Uri}
import zio.Task
import zio.json.*

final case class SpotifyAPI[F[_]](backend: SttpBackend[F, Any], accessToken: String)(using m: MonadError[F, Throwable]) {

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): F[SearchResult] = {
    val encodedTypes = entityTypes.map(_.toString.toLowerCase).mkString(",")
    val uri          = uri"${SpotifyAPI.API_BASE}/search?q=$query&type=$encodedTypes&limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getCurrentUserProfile: F[User] = {
    val uri = uri"${SpotifyAPI.API_BASE}/me"
    execute(uri, Method.GET)
  }

  def getUserProfile(userId: String): F[User] = {
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId"
    execute(uri, Method.GET)
  }

  def isValidEntity(entityId: String, entityType: EntityType): F[Boolean] =
    entityType match
      case EntityType.Album    => getAlbum(entityId).isSuccess
      case EntityType.Artist   => getArtist(entityId).isSuccess
      case EntityType.Playlist => getPlaylist(entityId).isSuccess
      case EntityType.Track    => getTrack(entityId).isSuccess

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): F[UserPlaylist] = {
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId?fields=$fields&market=$market"
    execute(uri, Method.GET)
  }

  def getTrack(id: String, market: Option[String] = None): F[Track] = {
    val uri = uri"${SpotifyAPI.API_BASE}/tracks/$id?market=$market"
    execute[Track](uri, Method.GET)
  }

  def getTracks(ids: Seq[String], market: Option[String] = None): F[Vector[Track]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/tracks?market=$market&ids=${ids.mkString(",")}"
    execute[MultiTrack](uri, Method.GET).map(_.tracks)
  }

  // TODO: fix this
  def getArtist(id: String): F[Artist] = getArtists(List(id)).map(_.head)

  def getArtists(ids: Seq[String]): F[Vector[Artist]] = {
    if (ids.length > 50) {
      m.raiseError(SpotifyError.MalformedRequest("Too many Artist IDs. Maximum allowed is 50"))
    } else {
      val uri = uri"${SpotifyAPI.API_BASE}/artists?ids=${ids.mkString(",")}"
      execute[MultiArtist](uri, Method.GET).map(_.artists)
    }
  }

  // TODO: fix this
  def getAlbum(id: String): F[Album] = getAlbums(List(id)).map(_.head)

  def getAlbums(ids: Seq[String]): F[Vector[Album]] = {
    if (ids.length > 20) {
      m.raiseError(SpotifyError.MalformedRequest("Too many Album IDs. Maximum allowed is 20"))
    } else {
      val uri = uri"${SpotifyAPI.API_BASE}/albums?ids=${ids.mkString(",")}"
      execute[MultiAlbum](uri, Method.GET).map(_.albums)
    }
  }

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): F[Paging[UserPlaylist]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllUserPlaylists(userId: String): F[Vector[UserPlaylist]] = {
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getUserPlaylists(userId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  // Seems like sometimes the inner tracks can be null?
  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None): F[Paging[PlaylistTrack]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllPlaylistTracks(playlistId: String): F[Vector[PlaylistTrack]] = {
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): F[Paging[Track]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/albums/$album/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllAlbumTracks(albumId: String): F[Vector[Track]] = {
    val MAX_PER_REQUEST = 20
    val request         = (offset: Int) => getSomeAlbumTracks(albumId, Some(MAX_PER_REQUEST), Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None): F[Paging[Album]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/artists/$artistId/albums?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllArtistAlbums(artistId: String): F[Vector[Album]] = {
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getSomeArtistAlbums(artistId, Some(MAX_PER_REQUEST), Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  // TODO: this endpoint is not documented properly by spotify. Country is a required parameter.
  def getArtistTopTracks(artistId: String, country: String = "US"): F[Vector[Track]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/artists/$artistId/top-tracks?country=$country"
    execute[MultiTrack](uri, Method.GET).map(_.tracks)
  }

  // TODO: impose limit on number of tracks.
  def checkUserSavedTracks(trackIds: Vector[String]): F[Vector[(String, Boolean)]] = {
    val distinct = trackIds.distinct
    val uri      = uri"${SpotifyAPI.API_BASE}/me/tracks/contains?ids=${distinct.mkString(",")}"
    execute[Vector[Boolean]](uri, Method.GET).map(distinct.zip(_))
  }

  def getAvailableDevices: F[Vector[PlaybackDevice]] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/devices"
    executeMaybeNoContent[PlaybackDevices](uri, Method.GET).map(_.fold(Vector.empty)(_.devices))

  def startPlayback(device: Option[String], startPlaybackBody: StartPlaybackBody): F[Boolean] = {
    val uri  = uri"${SpotifyAPI.API_BASE}/me/player/play?device_id=$device"
    val body = startPlaybackBody.toJson
    executeAndIgnoreResponse(uri, Method.PUT, Some(body)).as(true)
  }

  // device id must be active!
  def transferPlayback(deviceId: String): F[Boolean] =
    val uri  = uri"${SpotifyAPI.API_BASE}/me/player"
    val body = PlaybackDeviceIds(List(deviceId)).toJson
    executeAndIgnoreResponse(uri, Method.PUT, Some(body)).as(true)

  def saveTracks(trackIds: Vector[String]): F[Boolean] = {
    val uri = uri"${SpotifyAPI.API_BASE}/me/tracks/ids=${trackIds.distinct.mkString(",")}"
    executeAndIgnoreResponse(uri, Method.PUT).as(true)
  }

  def getPlaybackState: F[Option[PlaybackState]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/me/player"
    executeMaybeNoContent[PlaybackState](uri, Method.GET)
  }

  private def executeMaybeNoContent[T: JsonDecoder](uri: Uri, method: Method): F[Option[T]] =
    basicRequest
      .auth.bearer(accessToken)
      .response(asString)
      .method(method, uri)
      .send(backend).flatMap { response =>
        // TODO: build out json
        (response.body, response.code) match
          case (Right(""), StatusCode.NoContent) =>
            None.pure
          case (Right(body), s) if s.isSuccess   =>
            body.fromJson[T] match
              case Left(value)  => SpotifyError.JsonError(value, body, uri.toString).raiseError
              case Right(value) => Some(value).pure
          case (Left(error: String), _)          =>
            error.fromJson[ErrorResponse] match
              case Left(value)  => SpotifyError.JsonError(value, error, uri.toString).raiseError
              case Right(value) => SpotifyError.HttpError(value, uri, Method.GET).raiseError
      }

  def getAllPaging[T: JsonDecoder](request: Int => F[Paging[T]], pageSize: Int = 50): F[Vector[T]] = {
    def go(acc: Vector[T], offset: Int): F[Vector[T]] =
      request(offset).flatMap { (paging: Paging[T]) =>
        paging.next match
          case None    => (acc ++ paging.items).pure
          case Some(_) => go(acc ++ paging.items, offset + pageSize)
      }

    go(Vector.empty, 0)
  }

  def executeAndIgnoreResponse(uri: Uri, method: Method, body: Option[String] = None): F[Unit] =
    body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .method(method, uri)
      .response(asEither(asJsonAlways[ErrorResponse], ignore))
      .send(backend)
      .flatMap { response =>
        response.body match
          case Left(Right(error: ErrorResponse))                  =>
            m.raiseError(SpotifyError.HttpError(error, uri, method))
          case Left(Left(error @ DeserializationException(_, _))) =>
            SpotifyError.DeserializationException(error).raiseError
          case Right(_)                                           => ().pure
      }

  def execute[T: JsonDecoder](uri: Uri, method: Method, body: Option[String] = None): F[T] =
    body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .response(asJsonEither[ErrorResponse, T])
      .method(method, uri)
      .send(backend).flatMap { response =>
        response.body match
          case Left(HttpError(body, _))                     =>
            SpotifyError.HttpError(body, uri, method).raiseError
          case Left(error @ DeserializationException(_, _)) =>
            SpotifyError.DeserializationException(error).raiseError
          case Right(value)                                 => value.pure
      }
}

object SpotifyAPI {
  val API_BASE = "https://api.spotify.com/v1"
}
