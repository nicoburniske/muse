package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class SimplePlaylist(
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
    tracks: CollectionLink,
    uri: String
)

object SimplePlaylist {
  given JsonDecoder[SimplePlaylist] = DeriveJsonDecoder.gen[SimplePlaylist]
}
