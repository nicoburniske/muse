package service

import domain.spotify.{Paging, PlaylistTrack}
import sttp.client3.*
import sttp.model.{Method, ResponseMetadata, Uri}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import utils.Parallel
import zio.json.*

enum SpotifyRequestError extends Throwable {
  case HttpError(message: String, metadata: ResponseMetadata)
  case JsonError(error: String, received: String)
  case MultiError(errors: List[SpotifyRequestError])
  override def getMessage: String = {
    this match {
      case HttpError(message, metadata) => s"Error Code ${metadata.code}: $message"
      case JsonError(error, received)   => s"Json Error: $error}, Json received: \n $received"
      case MultiError(errors)           => "Errors: " + errors.map(_.getMessage()).mkString(", ")
    }
  }
}

type SpotifyResponse[T]        = Either[SpotifyRequestError, T]
type SpotifyPageResponse[T]    = SpotifyResponse[Paging[T]]
type SpotifyAllPageResponse[T] = Either[SpotifyRequestError, Vector[T]]

// TODO: separate Parallel queries into own domain?
case class Spotify[F[_]: MonadError: Parallel](
    backend: SttpBackend[F, Any],
    accessToken: String,
    refreshToken: String) {

  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): F[SpotifyPageResponse[PlaylistTrack]] = {
    val uri = uri"${Spotify.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllPlaylistTracks(playlistId: String): F[SpotifyAllPageResponse[PlaylistTrack]] = {
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  private def getAllPaging[T](request: Int => F[SpotifyPageResponse[T]], pageSize: Int = 50)(
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

  private def execute[T](uri: Uri, method: Method)(using decoder: JsonDecoder[T]): F[SpotifyResponse[T]] = {
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
  private def addPermissions[T](request: Request[T, Any]): Request[T, Any]                               = {
    request.auth.bearer(accessToken)
  }
}

object Spotify {
  val API_BASE = "https://api.spotify.com/v1"
}
