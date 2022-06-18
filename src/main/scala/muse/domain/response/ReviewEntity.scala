package muse.domain.response

import muse.domain.common.EntityType
import muse.domain.spotify.{
  AlbumType,
  PlaylistTrack,
  User,
  Album as SAlbum,
  Artist as SArtist,
  Track as STrack
}

sealed trait ReviewEntity

case class DetailedAlbum(
    albumGroup: Option[String],
    albumType: AlbumType,
    artists: List[SArtist],
    externalUrls: Map[String, String],
    genres: List[String],
    id: String,
    // Sort them largest to smallest?
    images: List[String],
    label: Option[String],
    name: String,
    popularity: Option[Int],
    releaseDate: String,
    tracks: List[STrack],
    entityType: EntityType)

case class DetailedTrack(
    album: SAlbum,
    artists: List[SArtist],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalUrls: Map[String, String],
    id: String,
    isPlayable: Boolean,
    name: String,
    popularity: Int,
    previewUrl: String,
    trackNumber: Int,
    isLocal: Boolean
)

// TODO: Add albums + Top tracks to artist?
case class DetailedArtist(
    externalUrls: Map[String, String],
    numFollowers: Int,
    genres: List[String],
    href: String,
    id: String,
    images: List[String],
    name: String,
    popularity: Int,
    entityType: EntityType,
    albums: List[SAlbum],
    topTracks: List[STrack]
)

case class DetailedPlaylist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    id: String,
    images: List[String],
    name: String,
    owner: User,
    primaryColor: Option[String],
    public: Boolean,
    tracks: List[PlaylistTrack],
    entityType: EntityType
)
