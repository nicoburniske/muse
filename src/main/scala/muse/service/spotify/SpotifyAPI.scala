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

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): F[SearchResult] =
    val encodedTypes = entityTypes.map(_.toString.toLowerCase).mkString(",")
    val uri          = uri"${SpotifyAPI.API_BASE}/search?q=$query&type=$encodedTypes&limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getTrackRecommendations(input: TrackRecsInput): F[MultiRecommendations] = {
    val uri = uri"${SpotifyAPI.API_BASE}/recommendations?${input.toUriString}"
    execute(uri, Method.GET)
  }

  def getCurrentUserProfile: F[User] =
    val uri = uri"${SpotifyAPI.API_BASE}/me"
    execute(uri, Method.GET)

  def getUserProfile(userId: String): F[User] =
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId"
    execute(uri, Method.GET)

  def isValidEntity(entityId: String, entityType: EntityType): F[Boolean] =
    entityType match
      case EntityType.Album    => getAlbum(entityId).isSuccess
      case EntityType.Artist   => getArtist(entityId).isSuccess
      case EntityType.Playlist => getPlaylist(entityId).isSuccess
      case EntityType.Track    => getTrack(entityId).isSuccess

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): F[UserPlaylist] =
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId?fields=$fields&market=$market"
    execute(uri, Method.GET)

  def getTrack(id: String, market: Option[String] = None): F[Track] =
    val uri = uri"${SpotifyAPI.API_BASE}/tracks/$id?market=$market"
    execute[Track](uri, Method.GET)

  def getTracks(ids: Vector[String], market: Option[String] = None): F[Vector[Track]] =
    val uri = uri"${SpotifyAPI.API_BASE}/tracks?market=$market&ids=${ids.mkString(",")}"
    execute[MultiTrack](uri, Method.GET).map(_.tracks)

  def getTrackAudioFeatures(id: String) =
    val uri = uri"${SpotifyAPI.API_BASE}/audio-features?id=$id"
    execute[AudioFeatures](uri, Method.GET)

  def getTracksAudioFeatures(ids: Vector[String]) =
    val uri = uri"${SpotifyAPI.API_BASE}/audio-features?ids=${ids.mkString(",")}"
    execute[MultiAudioFeatures](uri, Method.GET).map(_.audioFeatures)

  def getArtist(id: String): F[Artist] =
    val uri = uri"${SpotifyAPI.API_BASE}/artists/$id"
    execute[Artist](uri, Method.GET)

  def getArtists(ids: Vector[String]): F[Vector[Artist]] =
    if (ids.length > 50) {
      m.raiseError(SpotifyError.MalformedRequest("Too many Artist IDs. Maximum allowed is 50"))
    } else {
      val uri = uri"${SpotifyAPI.API_BASE}/artists?ids=${ids.mkString(",")}"
      execute[MultiArtist](uri, Method.GET).map(_.artists)
    }

  def getAlbum(id: String): F[Album] =
    val uri = uri"${SpotifyAPI.API_BASE}/albums/$id"
    execute[Album](uri, Method.GET)

  def getAlbums(ids: Vector[String]): F[Vector[Album]] =
    if (ids.length > 20) {
      m.raiseError(SpotifyError.MalformedRequest("Too many Album IDs. Maximum allowed is 20"))
    } else {
      val uri = uri"${SpotifyAPI.API_BASE}/albums?ids=${ids.mkString(",")}"
      execute[MultiAlbum](uri, Method.GET).map(_.albums)
    }

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): F[Paging[UserPlaylist]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllUserPlaylists(userId: String): F[Vector[UserPlaylist]] =
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getUserPlaylists(userId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  // Seems like sometimes the inner tracks can be null?
  def getSomePlaylistTracks(playlistId: String, limit: Int, offset: Option[Int] = None): F[Paging[PlaylistTrack]] =
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getAllPlaylistTracks(playlistId: String): F[Vector[PlaylistTrack]] =
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): F[Paging[Track]] =
    val uri = uri"${SpotifyAPI.API_BASE}/albums/$album/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getAllAlbumTracks(albumId: String): F[Vector[Track]] =
    val MAX_PER_REQUEST = 20
    val request         = (offset: Int) => getSomeAlbumTracks(albumId, Some(MAX_PER_REQUEST), Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  def getSomeArtistAlbums(artistId: String, limit: Option[Int] = None, offset: Option[Int] = None): F[Paging[Album]] =
    val uri = uri"${SpotifyAPI.API_BASE}/artists/$artistId/albums?limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getAllArtistAlbums(artistId: String): F[Vector[Album]] =
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getSomeArtistAlbums(artistId, Some(MAX_PER_REQUEST), Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  // TODO: this endpoint is not documented properly by spotify. Country is a required parameter.
  def getArtistTopTracks(artistId: String, country: String = "US"): F[Vector[Track]] =
    val uri = uri"${SpotifyAPI.API_BASE}/artists/$artistId/top-tracks?country=$country"
    execute[MultiTrack](uri, Method.GET).map(_.tracks)

  // TODO: impose limit on number of tracks.
  def checkUserSavedTracks(trackIds: Vector[String]): F[Vector[(String, Boolean)]] =
    val distinct = trackIds.distinct
    val uri      = uri"${SpotifyAPI.API_BASE}/me/tracks/contains?ids=${distinct.mkString(",")}"
    execute[Vector[Boolean]](uri, Method.GET).map(distinct.zip(_))

  def getAvailableDevices: F[Vector[PlaybackDevice]] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/devices"
    executeMaybeNoContent[PlaybackDevices](uri, Method.GET).map(_.fold(Vector.empty)(_.devices))

  def startPlayback(deviceId: Option[String], startPlaybackBody: Option[StartPlaybackBody]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/play?device_id=$deviceId"
    executeAndIgnoreResponse(uri, Method.PUT, startPlaybackBody.map(_.toJson)).as(true)

  // device id must be active!
  def transferPlayback(deviceId: String): F[Boolean] =
    val uri  = uri"${SpotifyAPI.API_BASE}/me/player"
    val body = PlaybackDeviceIds(List(deviceId)).toJson
    executeAndIgnoreResponse(uri, Method.PUT, Some(body)).as(true)

  def seekPlayback(deviceId: Option[String], positionMs: Int): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/seek?device_id=$deviceId&position_ms=$positionMs"
    executeAndIgnoreResponse(uri, Method.PUT).as(true)

  def saveTracks(trackIds: Vector[String]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/tracks?ids=${trackIds.distinct.mkString(",")}"
    executeAndIgnoreResponse(uri, Method.PUT).as(true)

  def removeSavedTracks(trackIds: Vector[String]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/tracks?ids=${trackIds.distinct.mkString(",")}"
    executeAndIgnoreResponse(uri, Method.DELETE).as(true)

  def getPlaybackState: F[Option[PlaybackState]] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player"
    executeMaybeNoContent[PlaybackState](uri, Method.GET)

  def pausePlayback(deviceId: Option[String]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/pause?device_id=$deviceId"
    executeAndIgnoreResponse(uri, Method.PUT).as(true)

  def skipToNext(deviceId: Option[String]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/next?device_id=$deviceId"
    executeAndIgnoreResponse(uri, Method.POST).as(true)

  def skipToPrevious(deviceId: Option[String]): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/previous?device_id=$deviceId"
    executeAndIgnoreResponse(uri, Method.POST).as(true)

  def toggleShuffle(shuffleState: Boolean): F[Boolean] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/player/shuffle?state=$shuffleState"
    executeAndIgnoreResponse(uri, Method.PUT).as(true)

  def getAllPaging[T: JsonDecoder](request: Int => F[Paging[T]], pageSize: Int = 50): F[Vector[T]] = {
    def go(acc: Vector[T], offset: Int): F[Vector[T]] =
      request(offset).flatMap { (paging: Paging[T]) =>
        paging.next match
          case None    => (acc ++ paging.items).pure
          case Some(_) => go(acc ++ paging.items, offset + pageSize)
      }

    go(Vector.empty, 0)
  }

  private def executeMaybeNoContent[T: JsonDecoder](uri: Uri, method: Method): F[Option[T]] =
    basicRequest
      .auth.bearer(accessToken)
      .response(asString)
      .method(method, uri)
      .send(backend).flatMap { response =>
        (response.body, response.code) match
          // Should only be Status.NoContent, but there are times when 200 + Empty body are used.
          case (Right(""), statusCode) if statusCode.isSuccess   =>
            None.pure
          case (Right(body), statusCode) if statusCode.isSuccess =>
            body.fromJson[T] match
              case Left(value)  =>
                SpotifyError.JsonError(value, body, uri.toString).raiseError
              case Right(value) =>
                Some(value).pure
          case (Left(errorBody: String), code)                   =>
            decodeError(uri, method, errorBody, code).raiseError
      }

  // TODO: Integrate Error 429 into this properly.
  // TODO: Add ref and clock here. need to enforce timeout.
  def executeAndIgnoreResponse(uri: Uri, method: Method, body: Option[String] = None): F[Unit] =
    body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .method(method, uri)
      .response(asString)
      .send(backend)
      .flatMap { response =>
        response.body match
          case Right(_)        => ().pure
          case Left(errorBody) => decodeError(uri, method, errorBody, response.code).raiseError
      }

  def execute[T: JsonDecoder](uri: Uri, method: Method, body: Option[String] = None): F[T] =
    body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .response(spotifyResponse(uri, method))
      .method(method, uri)
      .send(backend)
      .map(_.body)
      .flatMap {
        case Left(error)  => error.raiseError
        case Right(value) => value.pure
      }

  // TODO: Is there a case where we can get an HTTP Error with a non-error code?
  def spotifyResponse[B: JsonDecoder](uri: Uri, method: Method): ResponseAs[Either[SpotifyError, B], Any] =
    asJson[B].mapLeft {
      case HttpError(body, StatusCode.TooManyRequests)                            =>
        SpotifyError.TooManyRequests(uri, method, Some(body))
      case HttpError(errorBody, code) if code.isClientError || code.isServerError =>
        decodeError(uri, method, errorBody, code)
      case de @ DeserializationException(_, _)                                    =>
        SpotifyError.DeserializationException(uri, method, de)
    }

  private def decodeError(uri: Uri, method: Method, errorBody: String, code: StatusCode) =
    deserializeJson[ErrorResponse]
      .apply(errorBody)
      .fold(
        // Sometimes response body is not in common format and we only get an error string.
        message => SpotifyError.HttpError(Left(List(errorBody, message)), uri, method, code),
        // We will mostly get this case.
        errorResponse => SpotifyError.HttpError(Right(errorResponse), uri, method, code)
      )
}

object SpotifyAPI {
  val API_BASE = "https://api.spotify.com/v1"
}
