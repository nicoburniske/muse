package muse.server.graphql.subgraph

import muse.domain.spotify
import muse.server.graphql.Resolvers.{
  getAlbum,
  getAlbumTracks,
  getArtist,
  getArtistAlbums,
  getArtistTopTracks,
  getPlaylistTracks,
  spotifyProfile
}
import muse.server.graphql.subgraph
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

sealed trait ReviewEntity

case class Artist(
    externalUrls: Map[String, String],
    numFollowers: Int,
    genres: List[String],
    href: String,
    id: String,
    images: List[String],
    name: String,
    popularity: Int,
    // TODO: pagination.
    albums: ZQuery[SpotifyService, Throwable, List[Album]],
    topTracks: ZQuery[SpotifyService, Throwable, List[Track]]
) extends ReviewEntity

object Artist {
  def fromSpotify(a: spotify.Artist) = {
    Artist(
      a.externalUrls,
      a.followers.get.total,
      a.genres.getOrElse(Nil),
      a.href,
      a.id,
      a.images.fold(Nil)(_.map(_.url)),
      a.name,
      a.popularity.get,
      getArtistAlbums(a.id),
      getArtistTopTracks(a.id)
    )
  }
}

case class Album(
    albumGroup: Option[String],
    albumType: String,
    externalUrls: Map[String, String],
    genres: List[String],
    id: String,
    images: List[String],
    label: Option[String],
    name: String,
    popularity: Option[Int],
    releaseDate: String,
    artists: ZQuery[SpotifyService, Throwable, List[Artist]],
    tracks: ZQuery[SpotifyService, Throwable, List[Track]]
) extends ReviewEntity

object Album {
  def fromSpotify(a: spotify.Album) =
    val album = Album(
      a.albumGroup,
      a.albumType.toString.dropRight(1),
      a.externalUrls,
      a.genres.getOrElse(Nil),
      a.id,
      a.images.map(_.url),
      a.label,
      a.name,
      a.popularity,
      a.releaseDate,
      ZQuery.foreachPar(a.artists.map(_.id))(getArtist),
      getAlbumTracks(a.id, a.tracks.map(_.total))
    )
    album
}

case class Track(
    album: ZQuery[SpotifyService, Throwable, Album],
    artists: ZQuery[SpotifyService, Throwable, List[Artist]],
    //    artists: ZQuery[Any, Nothing, List[Artist]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalUrls: Map[String, String],
    href: String,
    id: String,
    isPlayable: Option[Boolean],
    name: String,
    popularity: Option[Int],
    previewUrl: Option[String],
    trackNumber: Int,
    isLocal: Boolean,
    uri: String
) extends ReviewEntity

object Track {
  def fromSpotify(t: spotify.Track, albumId: Option[String] = None) = {
    Track(
      getAlbum(t.album.map(_.id).orElse(albumId).get),
      ZQuery.foreachPar(t.artists.map(_.id))(getArtist),
      t.discNumber,
      t.durationMs,
      t.explicit,
      t.externalUrls,
      t.href,
      t.id,
      t.isPlayable,
      t.name,
      t.popularity,
      t.previewUrl,
      t.trackNumber,
      t.isLocal,
      t.uri
    )
  }
}

case class Playlist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    id: String,
    images: List[String],
    name: String,
    owner: User,
    primaryColor: Option[String],
    public: Option[Boolean],
    tracks: ZQuery[SpotifyService, Throwable, List[PlaylistTrack]]
    // tracks: Pagination => ZQuery[SpotifyService, Throwable, List[PlaylistTrack]]
) extends ReviewEntity

object Playlist {
  def fromSpotify(p: spotify.UserPlaylist) =
    Playlist(
      p.collaborative,
      p.description,
      p.externalUrls,
      p.id,
      p.images.map(_.url),
      p.name,
      User.missingSome(p.owner.id, p.owner.displayName, p.owner.href, p.owner.uri, p.owner.externalUrls),
      p.primaryColor,
      p.public,
      getPlaylistTracks(p.id, p.tracks.total)
    )
}
