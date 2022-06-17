package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.UserSessions
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.StatusCode
import zio.{Schedule, Task, ZIO}

object SpotifyService {
  val live = for {
    user    <- ZIO.service[UserSession]
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, user.accessToken)

  def live(accessToken: String) = for {
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, accessToken)

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
