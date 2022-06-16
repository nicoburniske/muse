package muse.service

import muse.domain.spotify.{MultiArtist, MultiTrack, Paging, PlaylistTrack, Track, User, UserPlaylist}
import sttp.client3.*
import sttp.model.{Method, ResponseMetadata, Uri}
import zio.json.*
import muse.domain.spotify.Artist
import muse.domain.spotify.MultiAlbum
import muse.domain.spotify.Album
import muse.utils.MonadError
import zio.Task

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

trait SpotifyService {
  def getCurrentUserProfile: Task[User]
  def getPlaylist(
      playlistId: String,
      fields: Option[String] = None,
      market: Option[String] = None): Task[UserPlaylist]
  def getTracks(ids: Seq[String], market: Option[String] = None): Task[Vector[Track]]
  def getArtists(ids: Seq[String]): Task[Vector[Artist]]
  def getAlbums(ids: Seq[String]): Task[Vector[Album]]
  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): Task[Paging[UserPlaylist]]
  def getAllUserPlaylists(userId: String): Task[Vector[UserPlaylist]]
  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): Task[Paging[PlaylistTrack]]
  def getAllPlaylistTracks(playlistId: String): Task[Vector[PlaylistTrack]]
}

case class SpotifyServiceLive[F[_]](backend: SttpBackend[F, Any], accessToken: String, refreshToken: String)(
    using m: MonadError[F, Throwable]) {

  def getCurrentUserProfile: F[User] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/me"
    execute(uri, Method.GET)
  }

  def getPlaylist(
      playlistId: String,
      fields: Option[String] = None,
      market: Option[String] = None): F[UserPlaylist] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/playlists/$playlistId?fields=$fields&market=$market"
    execute(uri, Method.GET)
  }

  def getTracks(ids: Seq[String], market: Option[String] = None): F[Vector[Track]] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/tracks?market=$market&ids=${ids.mkString(",")}"
    m.map(execute[MultiTrack](uri, Method.GET))(_.tracks)
  }

  // TODO: enforce limit
  def getArtists(ids: Seq[String]): F[Vector[Artist]] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/artists?ids=${ids.mkString(",")}"
    m.map(execute[MultiArtist](uri, Method.GET))(_.artists)
  }

  def getAlbums(ids: Seq[String]): F[Vector[Album]] = {
    if (ids.length > 20) {
      m.raiseError(SpotifyRequestError.MalformedRequest("Too many Album IDs. Maximum allowed is 20"))
    } else {
      val uri = uri"${SpotifyServiceLive.API_BASE}/albums?ids=${ids.mkString(",")}"
      m.map(execute[MultiAlbum](uri, Method.GET))(_.albums)
    }
  }

  def getUserPlaylists(userId: String, limit: Int, offset: Option[Int] = None): F[Paging[UserPlaylist]] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/users/$userId/playlists?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllUserPlaylists(userId: String): F[Vector[UserPlaylist]] = {
    val MAX_PER_REQUEST = 50
    val request         = (offset: Int) => getUserPlaylists(userId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getSomePlaylistTracks(
      playlistId: String,
      limit: Int,
      offset: Option[Int] = None): F[Paging[PlaylistTrack]] = {
    val uri = uri"${SpotifyServiceLive.API_BASE}/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
    execute(uri, Method.GET)
  }

  def getAllPlaylistTracks(playlistId: String): F[Vector[PlaylistTrack]] = {
    val MAX_PER_REQUEST = 100
    val request         = (offset: Int) => getSomePlaylistTracks(playlistId, MAX_PER_REQUEST, Some(offset))
    getAllPaging(request, MAX_PER_REQUEST)
  }

  def getAllPaging[T](request: Int => F[Paging[T]], pageSize: Int = 50)(
      using decoder: JsonDecoder[T]): F[Vector[T]] = {
    def go(acc: Vector[T], offset: Int): F[Vector[T]] = {
      m.flatMap(request(offset)) { (paging: Paging[T]) =>
        paging.next match {
          case None    => m.pure(acc ++ paging.items)
          case Some(_) => go(acc ++ paging.items, offset + pageSize)
        }
      }
    }

    go(Vector.empty, 0)
  }

  def execute[T](uri: Uri, method: Method)(using decoder: JsonDecoder[T]): F[T] = {
    val base            = basicRequest.copy[Identity, Either[String, String], Any](uri = uri, method = method)
    val withPermissions = addPermissions(base)
    val mappedResponse  = withPermissions.response.mapWithMetadata {
      case (Left(error), metadata) =>
        Left(SpotifyRequestError.HttpError(error, metadata))
      case (Right(response), _)    =>
        response.fromJson[T].left.map(SpotifyRequestError.JsonError(_, response))
    }
    val finalRequest    = withPermissions.response(mappedResponse)
    val sent            = backend.send(finalRequest)
    val body            = m.map(sent)(_.body)
    m.flatMap(body) {
      case Left(error) => m.raiseError(error)
      case Right(r)    => m.pure(r)
    }
  }

  private def addPermissions[T](request: Request[T, Any]): Request[T, Any] = request.auth.bearer(accessToken)
}

object SpotifyServiceLive {
  val API_BASE = "https://api.spotify.com/v1"
}
