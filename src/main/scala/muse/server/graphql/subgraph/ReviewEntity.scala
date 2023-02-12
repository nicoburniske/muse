package muse.server.graphql.subgraph

import caliban.schema.Annotations.GQLInterface
import muse.domain.spotify
import muse.server.graphql.resolver.{
  CheckUserLikedSong,
  GetAlbum,
  GetAlbumTracks,
  GetArtist,
  GetArtistAlbums,
  GetArtistTopTracks,
  GetPlaylistTracks,
  GetTrack,
  GetTrackAudioFeatures
}
import muse.server.graphql.subgraph
import muse.service.RequestSession
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

type SpotQuery[T] = ZQuery[RequestSession[SpotifyService], Throwable, T]

case class Artist(
    externalUrls: Map[String, String],
    numFollowers: SpotQuery[Int],
    genres: SpotQuery[List[String]],
    href: String,
    override val id: String,
    images: SpotQuery[List[String]],
    override val name: String,
    override val uri: String,
    popularity: SpotQuery[Int],
    // TODO: pagination.
    albums: SpotQuery[List[Album]],
    topTracks: SpotQuery[List[Track]]
) extends ReviewEntity

object Artist {
  def fromPlaybackState(a: spotify.Artist) = {
    val artist = GetArtist.query(a.id)
    Artist(
      a.externalUrls,
      artist.flatMap(_.numFollowers),
      artist.flatMap(_.genres),
      a.href,
      a.id,
      artist.flatMap(_.images),
      a.name,
      a.uri,
      artist.flatMap(_.popularity),
      GetArtistAlbums.query(a.id),
      GetArtistTopTracks.query(a.id)
    )
  }
  def fromSpotify(a: spotify.Artist)       = {
    Artist(
      a.externalUrls,
      ZQuery.succeed(a.followers.get.total),
      ZQuery.succeed(a.genres.getOrElse(Nil)),
      a.href,
      a.id,
      ZQuery.succeed(a.images.fold(Nil)(_.map(_.url))),
      a.name,
      a.uri,
      ZQuery.succeed(a.popularity.get),
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
    artists: ZQuery[RequestSession[SpotifyService], Throwable, List[Artist]],
    tracks: ZQuery[RequestSession[SpotifyService], Throwable, List[Track]]
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
    album: ZQuery[RequestSession[SpotifyService], Throwable, Album],
    artists: ZQuery[RequestSession[SpotifyService], Throwable, List[Artist]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalUrls: Map[String, String],
    href: String,
    override val id: String,
    isPlayable: ZQuery[RequestSession[SpotifyService], Throwable, Option[Boolean]],
    override val name: String,
    popularity: ZQuery[RequestSession[SpotifyService], Throwable, Int],
    previewUrl: Option[String],
    trackNumber: Int,
    isLocal: ZQuery[RequestSession[SpotifyService], Throwable, Boolean],
    uri: String,
    isLiked: ZQuery[RequestSession[SpotifyService], Throwable, Boolean],
    audioFeatures: ZQuery[RequestSession[SpotifyService], Throwable, spotify.AudioFeatures]
) extends ReviewEntity

object Track {
  def fromPlaybackState(t: spotify.Track) = {
    val artists = Some(t.artists)
      .filter(_.nonEmpty)
      .fold(ZQuery.foreachPar(t.artists.map(_.id))(GetArtist.query))(artists =>
        ZQuery.succeed(artists.map(Artist.fromPlaybackState)))
    Track(
      ZQuery.succeed(Album.fromSpotify(t.album)),
      artists,
      t.discNumber,
      t.durationMs,
      t.explicit,
      t.externalUrls,
      t.href,
      t.id,
      ZQuery.succeed(t.isPlayable),
      t.name,
      ZQuery.succeed(t.popularity),
      t.previewUrl,
      t.trackNumber,
      ZQuery.succeed(t.isLocal),
      t.uri,
      CheckUserLikedSong.query(t.id),
      GetTrackAudioFeatures.query(t.id)
    )

  }
  def fromSpotify(t: spotify.Track) = {
    Track(
      GetAlbum.query(t.album.id),
      ZQuery.foreachPar(t.artists.map(_.id))(GetArtist.query),
      t.discNumber,
      t.durationMs,
      t.explicit,
      t.externalUrls,
      t.href,
      t.id,
      ZQuery.succeed(t.isPlayable),
      t.name,
      ZQuery.succeed(t.popularity),
      t.previewUrl,
      t.trackNumber,
      ZQuery.succeed(t.isLocal),
      t.uri,
      CheckUserLikedSong.query(t.id),
      GetTrackAudioFeatures.query(t.id)
    )
  }

  def fromAlbum(t: spotify.AlbumTrack, albumId: String) = {
    val fullTrack = GetTrack.query(t.id)
    Track(
      GetAlbum.query(albumId),
      ZQuery.foreachPar(t.artists.map(_.id))(GetArtist.query),
      t.discNumber,
      t.durationMs,
      t.explicit,
      t.externalUrls,
      t.href,
      t.id,
      fullTrack.flatMap(_.isPlayable),
      t.name,
      fullTrack.flatMap(_.popularity),
      t.previewUrl,
      t.trackNumber,
      fullTrack.flatMap(_.isLocal),
      t.uri,
      CheckUserLikedSong.query(t.id),
      GetTrackAudioFeatures.query(t.id)
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
    tracks: ZQuery[RequestSession[SpotifyService], Throwable, List[PlaylistTrack]],
    numberOfTracks: Int,
    numberOfFollowers: Option[Int]
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
      GetPlaylistTracks.query(p.id, p.tracks.total),
      p.tracks.total,
      p.followers.map(_.total)
    )
}
