package muse.server.graphql.subgraph

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
    releaseDate: String
    //    artists: ZQuery[Any, Nothing, List[Artist]]
    //    tracks: Pagination => ZQuery[Any, Nothing, List[Track]]
    //    tracks: ZQuery[Any, Nothing, List[Track]]
) extends ReviewEntity

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

case class Playlist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    id: String,
    images: List[String],
    name: String,
    owner: SpotifyUser,
    primaryColor: Option[String],
    public: Boolean,
    tracks: ZQuery[SpotifyService, Throwable, List[PlaylistTrack]]
    // tracks: Pagination => ZQuery[SpotifyService, Throwable, List[PlaylistTrack]]
) extends ReviewEntity
