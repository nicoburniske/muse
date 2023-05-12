package muse.service.spotify

import muse.domain.common.EntityType
import muse.domain.spotify.*
import muse.utils.{Clock, MonadError, Ref}
import sttp.client3.*
import sttp.client3.ziojson.*
import sttp.model.{Method, ResponseMetadata, StatusCode, Uri}
import zio.Task
import zio.json.*

final case class SpotifyAPI[F[_]](backend: SttpBackend[F, Any], retryAfterRef: Ref[F, Option[Long]], accessToken: String)(
    using m: MonadError[F, Throwable],
    clock: Clock[F]) {

  def search(query: String, entityTypes: Set[EntityType], limit: Int = 50, offset: Option[Int] = None): F[SearchResult] =
    val encodedTypes = entityTypes.map(_.toString.toLowerCase).mkString(",")
    val uri          = uri"${SpotifyAPI.API_BASE}/search?q=$query&type=$encodedTypes&limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getTrackRecommendations(input: TrackRecsInput): F[MultiRecommendations] = {
    val uri = uri"${SpotifyAPI.API_BASE}/recommendations?${input.toUriString}"
    execute(uri, Method.GET)
  }

  def getCurrentUserProfile: F[PrivateUser] =
    val uri = uri"${SpotifyAPI.API_BASE}/me"
    execute(uri, Method.GET)

  def getUserProfile(userId: String): F[PublicUser] =
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId"
    if (userId.isBlank) {
      SpotifyError.MalformedRequest("User cannot be empty").raiseError
    } else {
      execute(uri, Method.GET)
    }

  def getPlaylist(playlistId: String, fields: Option[String] = None, market: Option[String] = None): F[SinglePlaylist] =
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId?fields=$fields&market=$market"
    execute(uri, Method.GET)

  def getTrack(id: String, market: Option[String] = None): F[Track] =
    val uri = uri"${SpotifyAPI.API_BASE}/tracks/$id?market=$market"
    execute[Track](uri, Method.GET)

  def getTracks(ids: Vector[String], market: Option[String] = None): F[Vector[Track]] =
    val uri = uri"${SpotifyAPI.API_BASE}/tracks?market=$market&ids=${ids.mkString(",")}"
    execute[MultiTrack](uri, Method.GET).map(_.tracks)

  def getTrackAudioAnalysis(id: String) =
    val uri = uri"${SpotifyAPI.API_BASE}/audio-analysis/$id"
    execute[AudioAnalysis](uri, Method.GET)

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

  def getCurrentUserPlaylists(limit: Int = 50, offset: Option[Int] = None): F[Paging[BulkPlaylist]] =
    val uri = uri"${SpotifyAPI.API_BASE}/me/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): F[Paging[BulkPlaylist]] = {
    val uri = uri"${SpotifyAPI.API_BASE}/users/$userId/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllUserPlaylists(userId: String): F[Vector[BulkPlaylist]] =
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getUserPlaylists(userId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  // Seems like sometimes the inner tracks can be null?
  // When people include local tracks in playlist.
  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): F[Paging[Either[Throwable, PlaylistTrack]]] =
    val uri = uri"${SpotifyAPI.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"

    // TODO: figure out if there's a better way to do this!
    val maybePlaylistTrackDecoder = JsonDecoder[zio.json.ast.Json].map { json =>
      SpotifyAPI.DeserializationException(json.toString(), "Failed to Deserialize Playlist Track")
    }

    given JsonDecoder[Either[Throwable, PlaylistTrack]] =
      JsonDecoder[PlaylistTrack].orElseEither(maybePlaylistTrackDecoder).map(_.swap)

    execute(uri, Method.GET)

  def getAllPlaylistTracks(playlistId: String): F[Vector[Either[Throwable, PlaylistTrack]]] =
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)

  def getSomeAlbumTracks(album: String, limit: Option[Int] = None, offset: Option[Int] = None): F[Paging[SimpleTrack]] =
    val uri = uri"${SpotifyAPI.API_BASE}/albums/$album/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)

  def getAllAlbumTracks(albumId: String): F[Vector[SimpleTrack]] =
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
    val body = TransferPlaybackBody(List(deviceId)).toJson
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

  def getAllPaging[T](request: Int => F[Paging[T]], pageSize: Int = 50): F[Vector[T]] = {
    def go(acc: Vector[T], offset: Int): F[Vector[T]] =
      request(offset).flatMap { (paging: Paging[T]) =>
        paging.next match
          case None    => (acc ++ paging.items).pure
          case Some(_) => go(acc ++ paging.items, offset + pageSize)
      }

    go(Vector.empty, 0)
  }

  private def executeMaybeNoContent[T: JsonDecoder](uri: Uri, method: Method): F[Option[T]] =
    ensureNoRateLimit *> basicRequest
      .auth.bearer(accessToken)
      .response(asString)
      .method(method, uri)
      .send(backend).flatMap { response =>
        (response.body, response.code) match
          // Should only be Status.NoContent, but there are times when 200 + Empty body are used.
          case (Right(""), statusCode) if statusCode.isSuccess                       =>
            None.pure
          case (Right(body), statusCode) if statusCode.isSuccess                     =>
            body.fromJson[T] match
              case Left(value)  =>
                SpotifyError.JsonError(value, body, uri.toString, method, response).raiseError
              case Right(value) =>
                Some(value).pure
          case (Left(errorBody: String), code) if code == StatusCode.TooManyRequests =>
            checkRateLimit(response) *>
              decodeError(uri, method, errorBody, response).raiseError
          case (Left(errorBody: String), code)                                       =>
            decodeError(uri, method, errorBody, response).raiseError
      }

  def executeAndIgnoreResponse(uri: Uri, method: Method, body: Option[String] = None): F[Unit] =
    ensureNoRateLimit *> body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .method(method, uri)
      .response(asString)
      .send(backend)
      .flatMap { response =>
        response.body match
          case Right(_)        => ().pure
          case Left(errorBody) =>
            checkRateLimit(response) *> decodeError(uri, method, errorBody, response).raiseError
      }

  def execute[T: JsonDecoder](uri: Uri, method: Method, body: Option[String] = None): F[T] =
    ensureNoRateLimit *> body
      .fold(basicRequest)(basicRequest.body(_))
      .auth.bearer(accessToken)
      .response(spotifyResponse(uri, method))
      .method(method, uri)
      .send(backend)
      .map(_.body)
      .flatMap {
        case Left(error @ SpotifyError.TooManyRequests(_, _, _, metadata)) =>
          checkRateLimit(metadata) *> error.raiseError
        case Left(error)                                                   => error.raiseError
        case Right(value)                                                  => value.pure
      }

  def checkRateLimit(metadata: ResponseMetadata) = {
    metadata.header("Retry-After").map(_.toInt) match
      case None                => ().pure
      case Some(secondsToWait) => clock.now.flatMap { now => retryAfterRef.set(Some(now + secondsToWait)) }
  }

  def ensureNoRateLimit: F[Unit] =
    for {
      limit <- retryAfterRef.get
      now   <- clock.now
      _     <- limit.fold(().pure) { limitTime =>
                 if (limitTime > now) {
                   SpotifyError.RateLimited(limitTime).raiseError
                 } else ().pure
               }
    } yield ()

  def spotifyResponse[B: JsonDecoder](uri: Uri, method: Method): ResponseAs[Either[SpotifyError, B], Any] =
    asJson[B].mapLeft {
      case SpotifyAPI.HttpError(body, metadata) if metadata.code == StatusCode.TooManyRequests                     =>
        SpotifyError.TooManyRequests(uri, method, Some(body), metadata)
      case SpotifyAPI.HttpError(errorBody, metadata) if metadata.code.isClientError || metadata.code.isServerError =>
        decodeError(uri, method, errorBody, metadata)
      case de @ SpotifyAPI.DeserializationException(_, _)                                                          =>
        SpotifyError.DeserializationException(uri, method, de)
    }

  private def decodeError(uri: Uri, method: Method, errorBody: String, metadata: ResponseMetadata) =
    deserializeJson[ErrorResponse]
      .apply(errorBody)
      .fold(
        // Sometimes response body is not in common format and we only get an error string.
        message => SpotifyError.HttpError(Left(List(errorBody, message)), uri, method, metadata),
        // We will mostly get this case.
        errorResponse => SpotifyError.HttpError(Right(errorResponse), uri, method, metadata)
      )

  given stringShowError: ShowError[String] = t => t

  def asJson[B: JsonDecoder: IsOption]: ResponseAs[Either[SpotifyAPI.ResponseException[String, String], B], Any] =
    asString.mapWithMetadata(deserializeRightWithError(deserializeJson))

  def deserializeRightWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): (Either[String, String], ResponseMetadata) => Either[SpotifyAPI.ResponseException[String, E], T] = {
    case (Left(s), meta) => Left(SpotifyAPI.HttpError(s, meta))
    case (Right(s), _)   => deserializeWithError(doDeserialize)(implicitly[ShowError[E]])(s)
  }

  def deserializeWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): String => Either[SpotifyAPI.DeserializationException[E], T] =
    s =>
      doDeserialize(s) match {
        case Left(e)  => Left(SpotifyAPI.DeserializationException(s, e))
        case Right(b) => Right(b)
      }

}

object SpotifyAPI {
  val API_BASE = "https://api.spotify.com/v1"

  sealed abstract class ResponseException[+HE, +DE](error: String) extends Exception(error)

  final case class HttpError[HE](body: HE, metadata: ResponseMetadata)
      extends ResponseException[HE, Nothing](s"statusCode: ${metadata.code}, response: $body")

  final case class DeserializationException[DE: ShowError](body: String, error: DE)
      extends ResponseException[Nothing, DE](implicitly[ShowError[DE]].show(error))
}
