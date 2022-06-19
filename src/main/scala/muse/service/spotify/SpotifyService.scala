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
import zio.{Schedule, Task, ZIO}

// TODO: Determine if we can use graphql here as middleware with spotify
// Would make building more modular and most case classes would be generated from schema.
object SpotifyService {
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
                                 s.getArtistAlbums(artistId) <&>
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
