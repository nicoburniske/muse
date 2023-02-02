package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class UserPlaylist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    href: String,
    id: String,
    images: List[Image],
    name: String,
    owner: User,
    primaryColor: Option[String],
    public: Option[Boolean],
    snapshotID: String,
    tracks: PlaylistTracks,
    `type`: String,
    uri: String,
    // Followers is null from search api.
    followers: Option[Followers]
)

object UserPlaylist {
  given decodeUserPlaylist: JsonDecoder[UserPlaylist] = DeriveJsonDecoder.gen[UserPlaylist]
}
