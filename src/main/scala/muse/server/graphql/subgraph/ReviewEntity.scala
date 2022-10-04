package muse.server.graphql.subgraph

import caliban.schema.Annotations.GQLInterface
import muse.domain.spotify
import muse.server.graphql.resolver.{CheckUserLikedSong, GetAlbum, GetAlbumTracks, GetArtist, GetArtistAlbums, GetArtistTopTracks, GetPlaylistTracks}
import muse.server.graphql.subgraph
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

/**
 * Represents a Spotify Entity that can be reviewed.
 */
@GQLInterface
sealed trait ReviewEntity {
  // TODO: incorporate id into each type.
  def name: String
  def id: String
  def uri: String
}

case class Artist(
    externalUrls: Map[String, String],
    numFollowers: Int,
    genres: List[String],
    href: String,
    override val id: String,
    images: List[String],
    override val name: String,
    override val uri: String,
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
      a.uri,
      a.popularity.get,
      GetArtistAlbums.query(a.id),
      GetArtistTopTracks.query(a.id)
    )
  }
}

case class Album(
    albumGroup: Option[String],
    albumType: String,
    externalUrls: Map[String, String],
    genres: List[String],
    override val id: String,
    images: List[String],
    label: Option[String],
    override val name: String,
    popularity: Option[Int],
    releaseDate: String,
    override val uri: String,
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
      a.uri,
      ZQuery.foreachPar(a.artists.map(_.id))(GetArtist.query),
      GetAlbumTracks.query(a.id, a.tracks.map(_.total))
    )
    album
}

case class Track(
    album: ZQuery[SpotifyService, Throwable, Album],
    artists: ZQuery[SpotifyService, Throwable, List[Artist]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalUrls: Map[String, String],
    href: String,
    override val id: String,
    isPlayable: Option[Boolean],
    override val name: String,
    popularity: Option[Int],
    previewUrl: Option[String],
    trackNumber: Int,
    isLocal: Boolean,
    uri: String,
    isLiked: ZQuery[SpotifyService, Throwable, Boolean]
) extends ReviewEntity

object Track {
  def fromSpotify(t: spotify.Track, albumId: Option[String] = None) = {
    Track(
      GetAlbum.query(t.album.map(_.id).orElse(albumId).get),
      ZQuery.foreachPar(t.artists.map(_.id))(GetArtist.query),
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
      t.uri,
      CheckUserLikedSong.query(t.id)
    )
  }
}

// TODO: include followers?
case class Playlist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    override val id: String,
    images: List[String],
    override val name: String,
    override val uri: String,
    owner: User,
    primaryColor: Option[String],
    public: Option[Boolean],
    tracks: ZQuery[SpotifyService, Throwable, List[PlaylistTrack]]
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
      p.uri,
      User.missingSome(p.owner.id, p.owner.displayName, p.owner.href, p.owner.uri, p.owner.externalUrls),
      p.primaryColor,
      p.public,
      GetPlaylistTracks.query(p.id, p.tracks.total)
    )
}
