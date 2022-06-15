package muse.domain.spotify

import zio.json.*
import muse.domain.common.{Entity, EntityType}

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
    public: Boolean,
    @jsonField("snapshot_id")
    snapshotID: String,
    tracks: PlaylistTracks,
    @jsonField("type")
    entityType: String,
    uri: String
) extends Entity(id, EntityType.Playlist)

object UserPlaylist {
  given decodeUserPlaylist: JsonDecoder[UserPlaylist] = DeriveJsonDecoder.gen[UserPlaylist]
}