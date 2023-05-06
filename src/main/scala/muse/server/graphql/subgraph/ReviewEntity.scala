package muse.server.graphql.subgraph

import caliban.schema.Annotations.GQLInterface
import muse.domain.common.Types.UserId
import muse.domain.spotify
import muse.server.graphql.resolver.*
import muse.server.graphql.subgraph
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

/**
 * Represents a Spotify Entity that can be reviewed.
 */
@GQLInterface
sealed trait ReviewEntity {
  def id: String
  def uri: String
  def href: String
  def name: String
}

type SpotQuery[T] = ZQuery[SpotifyService, Throwable, T]

case class Artist(
    href: String,
    id: String,
    uri: String,
    name: String,
    // Has to be wrapped with a query to be consistent with other entities.
    externalUrls: SpotQuery[Map[String, String]],
    // From full artist.
    numFollowers: SpotQuery[Int],
    genres: SpotQuery[List[String]],
    images: SpotQuery[List[String]],
    popularity: SpotQuery[Int],
    // TODO: pagination.
    // Different queries.
    albums: SpotQuery[List[Album]],
    topTracks: SpotQuery[List[Track]]
) extends ReviewEntity

object Artist {
  def fromSpotifySimple(a: spotify.SimpleArtist): Artist = {
    val artist = GetArtist.query(a.id)
    Artist(
      a.href,
      a.id,
      a.uri,
      a.name,
      ZQuery.succeed(a.externalUrls),
      artist.flatMap(_.numFollowers),
      artist.flatMap(_.genres),
      artist.flatMap(_.images),
      artist.flatMap(_.popularity),
      GetArtistAlbums.query(a.id),
      GetArtistTopTracks.query(a.id)
    )
  }
  def fromSpotify(a: spotify.Artist)                     = {
    Artist(
      a.href,
      a.id,
      a.uri,
      a.name,
      ZQuery.succeed(a.externalUrls),
      ZQuery.succeed(a.followers.total),
      ZQuery.succeed(a.genres),
      ZQuery.succeed(a.images.map(_.url)),
      ZQuery.succeed(a.popularity),
      GetArtistAlbums.query(a.id),
      GetArtistTopTracks.query(a.id)
    )
  }
}

case class Album(
    href: String,
    id: String,
    uri: String,
    name: String,
    albumType: spotify.AlbumType,
    availableMarkets: List[String],
    releaseDate: String,
    releaseDatePrecision: spotify.ReleaseDatePrecision,
    images: List[String],
    // From full album.
    externalUrls: SpotQuery[Map[String, String]],
    externalIds: SpotQuery[spotify.ExternalIds],
    copyrights: SpotQuery[List[spotify.Copyright]],
    genres: SpotQuery[List[String]],
    label: SpotQuery[String],
    popularity: SpotQuery[Int],
    // Distinct queries.
    artists: SpotQuery[List[Artist]],
    tracks: SpotQuery[List[Track]]
) extends ReviewEntity

object Album {
  def fromSpotify(a: spotify.Album) =
    Album(
      a.href,
      a.id,
      a.uri,
      a.name,
      a.albumType,
      a.availableMarkets,
      a.releaseDate,
      a.releaseDatePrecision,
      a.images.map(_.url),
      ZQuery.succeed(a.externalUrls),
      ZQuery.succeed(a.externalIds),
      ZQuery.succeed(a.copyrights),
      ZQuery.succeed(a.genres),
      ZQuery.succeed(a.label),
      ZQuery.succeed(a.popularity),
      ZQuery.succeed(a.artists.map(Artist.fromSpotifySimple)),
      GetAlbumTracks.query(a.id, Some(a.tracks.total))
    )

  def fromSpotifySimple(a: spotify.SimpleAlbum): Album = {
    val query   = GetAlbum.query(a.id)
    val artists = a.artists.fold(query.flatMap(_.artists)) { artists => ZQuery.succeed(artists.map(Artist.fromSpotifySimple)) }
    Album(
      a.href,
      a.id,
      a.uri,
      a.name,
      a.albumType,
      a.availableMarkets,
      a.releaseDate,
      a.releaseDatePrecision,
      a.images.map(_.url),
      query.flatMap(_.externalUrls),
      query.flatMap(_.externalIds),
      query.flatMap(_.copyrights),
      query.flatMap(_.genres),
      query.flatMap(_.label),
      query.flatMap(_.popularity),
      artists,
      query.flatMap(_.tracks)
    )
  }
}

case class Track(
    href: String,
    id: String,
    uri: String,
    name: String,
    durationMs: Int,
    discNumber: Int,
    trackNumber: Int,
    explicit: Boolean,
    isLocal: Boolean,
    isPlayable: Option[Boolean],
    previewUrl: Option[String],
    externalIds: Option[spotify.ExternalIds],
    restrictions: Option[spotify.Restrictions],
    artists: List[Artist],
    // From full track.
    externalUrls: SpotQuery[Map[String, String]],
    availableMarkets: SpotQuery[List[String]],
    popularity: SpotQuery[Int],
    // Distinct queries.
    album: SpotQuery[Album],
    isLiked: SpotQuery[Boolean],
    audioAnalysis: SpotQuery[spotify.AudioAnalysis],
    audioFeatures: SpotQuery[spotify.AudioFeatures]
) extends ReviewEntity

object Track {
  def fromSpotifySimple(t: spotify.SimpleTrack, albumId: Option[String]) = {
    val query        = GetTrack.query(t.id)
    val album        = albumId.fold(query.flatMap(_.album))(GetAlbum.query)
    val externalUrls = t.externalUrls.fold(query.flatMap(_.externalUrls))(ZQuery.succeed)

    Track(
      t.href,
      t.id,
      t.uri,
      t.name,
      t.durationMs,
      t.discNumber,
      t.trackNumber,
      t.explicit,
      t.isLocal,
      t.isPlayable,
      t.previewUrl,
      t.externalIds,
      t.restrictions,
      t.artists.map(Artist.fromSpotifySimple),
      externalUrls,
      query.flatMap(_.availableMarkets),
      query.flatMap(_.popularity),
      album,
      CheckUserLikedSong.query(t.id),
      GetTrackAudioAnalysis.query(t.id),
      GetTrackAudioFeatures.query(t.id)
    )

  }
  def fromSpotify(t: spotify.Track) = {
    Track(
      t.href,
      t.id,
      t.uri,
      t.name,
      t.durationMs,
      t.discNumber,
      t.trackNumber,
      t.explicit,
      t.isLocal,
      t.isPlayable,
      t.previewUrl,
      t.externalIds,
      t.restrictions,
      t.artists.map(Artist.fromSpotifySimple),
      ZQuery.succeed(t.externalUrls),
      ZQuery.succeed(t.availableMarkets),
      ZQuery.succeed(t.popularity),
      ZQuery.succeed(Album.fromSpotifySimple(t.album)),
      CheckUserLikedSong.query(t.id),
      GetTrackAudioAnalysis.query(t.id),
      GetTrackAudioFeatures.query(t.id)
    )
  }
}

case class Playlist(
    href: String,
    id: String,
    uri: String,
    name: String,
    collaborative: Boolean,
    public: Option[Boolean],
    description: Option[String],
    numberOfTracks: Int,
    images: List[String],
    owner: User,
    externalUrls: SpotQuery[Map[String, String]],
    snapshotId: SpotQuery[String],
    numberOfFollowers: SpotQuery[Int],
    tracks: SpotQuery[List[PlaylistTrack]]
) extends ReviewEntity

object Playlist {
  def fromSpotify(p: spotify.SinglePlaylist) = Playlist(
    p.href,
    p.id,
    p.uri,
    p.name,
    p.collaborative,
    p.public,
    p.description,
    p.tracks.total,
    p.images.map(_.url),
    GetUser.queryByUserId(UserId(p.owner.id)),
    ZQuery.succeed(p.externalUrls),
    ZQuery.succeed(p.snapshotId),
    ZQuery.succeed(p.followers.total),
    GetPlaylistTracks.query(p.id, p.tracks.total)
  )

  def fromSpotify(p: spotify.BulkPlaylist) =
    Playlist(
      p.href,
      p.id,
      p.uri,
      p.name,
      p.collaborative,
      p.public,
      p.description,
      p.tracks.total,
      p.images.map(_.url),
      GetUser.queryByUserId(UserId(p.owner.id)),
      ZQuery.succeed(p.externalUrls),
      ZQuery.succeed(p.snapshotId),
      GetPlaylist.query(p.id).flatMap(_.numberOfFollowers),
      GetPlaylistTracks.query(p.id, p.tracks.total)
    )

  def fromSpotifySimple(p: spotify.SimplePlaylist) =
    val query = GetPlaylist.query(p.id)
    Playlist(
      p.href,
      p.id,
      p.uri,
      p.name,
      p.collaborative,
      p.public,
      p.description,
      p.tracks.total,
      p.images.map(_.url),
      GetUser.queryByUserId(UserId(p.owner.id)),
      ZQuery.succeed(p.externalUrls),
      query.flatMap(_.snapshotId),
      query.flatMap(_.numberOfFollowers),
      GetPlaylistTracks.query(p.id, p.tracks.total)
    )
}
