package muse.service.spotify

import zio.{Schedule, Task, ZIO}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.StatusCode
import muse.domain.spotify.*
import muse.domain.tables.AppUser
import muse.config.SpotifyConfig
import muse.service.UserSessions
import muse.utils.Givens.given

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

object SpotifyService {
  val live = for {
    user     <- ZIO.service[AppUser]
    config   <- ZIO.service[SpotifyConfig]
    sessions <- ZIO.service[UserSessions]
    backend  <- ZIO.service[SttpBackend[Task, Throwable]]
  } yield SpotifyServiceLive(backend, user.accessToken)
}
