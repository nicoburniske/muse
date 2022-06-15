package muse.service

import muse.domain.spotify.{MultiTrack, MultiArtist, Paging, PlaylistTrack, Track, UserPlaylist, User}
import sttp.client3.*
import sttp.model.{Method, ResponseMetadata, Uri}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import muse.utils.Parallel
import zio.json.*
import muse.domain.spotify.Artist
import muse.domain.spotify.MultiAlbum
import muse.domain.spotify.Album

enum SpotifyRequestError extends Throwable {
  case MalformedRequest(reason: String)
  case HttpError(message: String, metadata: ResponseMetadata)
  case JsonError(error: String, received: String)
  override def getMessage: String = {
    this match {
      case MalformedRequest(reason: String) => reason
      case HttpError(message, metadata)     => s"Error Code ${metadata.code}: $message"
      case JsonError(error, received)       => s"Json Error: $error}, Json received: \n $received"
    }
  }
}

type SpotifyResponse[T]        = Either[SpotifyRequestError, T]
type SpotifyPageResponse[T]    = SpotifyResponse[Paging[T]]
type SpotifyAllPageResponse[T] = Either[SpotifyRequestError, Vector[T]]

case class SpotifyService[F[_]: MonadError](
    backend: SttpBackend[F, Any],
    accessToken: String,
    refreshToken: String) {

  def getCurrentUserProfile: F[SpotifyResponse[User]] = {
    val uri = uri"${SpotifyService.API_BASE}/me"
    execute(uri, Method.GET)
  }

  def getPlaylist(
      playlistId: String,
      fields: Option[String] = None,
      market: Option[String] = None): F[SpotifyResponse[UserPlaylist]] = {
    val uri = uri"${SpotifyService.API_BASE}/playlists/$playlistId?fields=$fields&market=$market"
    execute(uri, Method.GET)
  }

  def getTracks(ids: Seq[String], market: Option[String] = None): F[SpotifyResponse[Vector[Track]]] = {
    val uri = uri"${SpotifyService.API_BASE}/tracks?market=$market&ids=${ids.mkString(",")}"
    for {
      response <- execute[MultiTrack](uri, Method.GET)
    } yield {
      response.map(_.tracks)
    }
  }

  // TODO: enforce limit
  def getArtists(ids: Seq[String]): F[SpotifyResponse[Vector[Artist]]] = {
    val uri = uri"${SpotifyService.API_BASE}/artists?ids=${ids.mkString(",")}"
    for {
      response <- execute[MultiArtist](uri, Method.GET)
    } yield response.map(_.artists)
  }

  // TODO: enforce limit
  def getAlbums(ids: Seq[String]): F[SpotifyResponse[Vector[Album]]] = {
    val uri = uri"${SpotifyService.API_BASE}/albums?ids=${ids.mkString(",")}"
    for {
      response <- execute[MultiAlbum](uri, Method.GET)
    } yield response.map(_.albums)
  }

  def getUserPlaylists(
      userId: String,
      limit: Int,
      offset: Option[Int] = None): F[SpotifyPageResponse[UserPlaylist]] = {
    val uri = uri"${SpotifyService.API_BASE}/users/$userId/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllUserPlaylists(userId: String): F[SpotifyAllPageResponse[UserPlaylist]] = {
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getUserPlaylists(userId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): F[SpotifyPageResponse[PlaylistTrack]] = {
    val uri = uri"${SpotifyService.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllPlaylistTracks(playlistId: String): F[SpotifyAllPageResponse[PlaylistTrack]] = {
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getAllPaging[T](request: Int => F[SpotifyPageResponse[T]], pageSize: Int = 50)(
      using decoder: JsonDecoder[T]): F[SpotifyAllPageResponse[T]] = {
    def go(acc: Vector[T], offset: Int): F[SpotifyAllPageResponse[T]] = {
      request(offset).flatMap {
        case Left(error)              =>
          MonadError[F].unit(Left(error))
        case Right(paging: Paging[T]) =>
          paging.next match {
            case None    => MonadError[F].unit(Right(acc ++ paging.items))
            case Some(_) => go(acc ++ paging.items, offset + pageSize)
          }
      }
    }

    go(Vector.empty, 0)
  }

  def execute[T](uri: Uri, method: Method)(using decoder: JsonDecoder[T]): F[SpotifyResponse[T]] = {
    val base            = basicRequest.copy[Identity, Either[String, String], Any](uri = uri, method = method)
    val withPermissions = addPermissions(base)
    val mappedResponse  = withPermissions.response.mapWithMetadata {
      case (Left(error), metadata) =>
        Left(SpotifyRequestError.HttpError(error, metadata))
      case (Right(response), _)    =>
        response.fromJson[T].left.map(SpotifyRequestError.JsonError(_, response))
    }
    val finalRequest    = withPermissions.response(mappedResponse)
    backend.send(finalRequest).map(_.body)
  }

  private def addPermissions[T](request: Request[T, Any]): Request[T, Any] = request.auth.bearer(accessToken)
}

object SpotifyService {
  val API_BASE = "https://api.spotify.com/v1"
}
