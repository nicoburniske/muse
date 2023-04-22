package muse.domain.spotify

import zio.json.*

// Bulk endpoints don't include followers.
@jsonMemberNames(SnakeCase)
final case class BulkPlaylist(
    collaborative: Boolean,
    description: Option[String],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    images: List[Image],
    name: String,
    owner: PlaylistUser,
    public: Option[Boolean],
    snapshotId: String,
    tracks: PlaylistTracks,
    `type`: String,
    uri: String
)

object BulkPlaylist {
  given decodeUserPlaylist: JsonDecoder[BulkPlaylist] = DeriveJsonDecoder.gen[BulkPlaylist]
}
