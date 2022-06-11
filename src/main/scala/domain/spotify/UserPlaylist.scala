package domain.spotify

import zio.json.*
import domain.common.EntityType

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
    `type`: EntityType,
    uri:String 
)

object UserPlaylist {
    given decodeUserPlaylist: JsonDecoder[UserPlaylist] = DeriveJsonDecoder.gen[UserPlaylist] 
}
