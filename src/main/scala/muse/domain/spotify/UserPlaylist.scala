package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

final case class UserPlaylist(
    collaborative: Boolean,
    description: String,
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    href: String,
    id: String,
    images: List[Image],
    name: String,
    owner: User,
    @jsonField("primary_color")
    primaryColor: Option[String],
    public: Option[Boolean],
    @jsonField("snapshot_id")
    snapshotID: String,
    tracks: PlaylistTracks,
    @jsonField("type")
    entityType: String,
    uri: String,
    followers: Followers
) extends Entity(id, EntityType.Playlist)

object UserPlaylist {
  given decodeUserPlaylist: JsonDecoder[UserPlaylist] = DeriveJsonDecoder.gen[UserPlaylist]
}
