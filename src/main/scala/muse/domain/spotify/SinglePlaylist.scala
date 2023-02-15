package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class SinglePlaylist(
    collaborative: Boolean,
    description: Option[String],
    externalUrls: Map[String, String],
    followers: Followers,
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

object SinglePlaylist {
  given JsonDecoder[SinglePlaylist] = DeriveJsonDecoder.gen[SinglePlaylist]
}
