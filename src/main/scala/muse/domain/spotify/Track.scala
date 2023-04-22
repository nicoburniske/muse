package muse.domain.spotify

import muse.domain.common.EntityType
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class Track(
    album: SimpleAlbum,
    artists: List[SimpleArtist],
    availableMarkets: List[String],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalIds: Option[ExternalIds],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    isPlayable: Option[Boolean],
    linkedFrom: Option[LinkedTrack],
    restrictions: Option[Restrictions],
    name: String,
    popularity: Int,
    previewUrl: Option[String],
    trackNumber: Int,
    `type`: String,
    uri: String,
    isLocal: Boolean
)

object Track {
  given decodeTrack: JsonDecoder[Track] = DeriveJsonDecoder.gen[Track]
}
