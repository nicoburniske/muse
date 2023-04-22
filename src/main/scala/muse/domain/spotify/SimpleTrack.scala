package muse.domain.spotify

import muse.domain.common.EntityType
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class SimpleTrack(
    artists: List[SimpleArtist],
    availableMarkets: Option[List[String]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalIds: Option[ExternalIds],
    externalUrls: Option[Map[String, String]],
    href: String,
    id: String,
    isPlayable: Option[Boolean],
    linkedFrom: Option[LinkedTrack],
    restrictions: Option[Restrictions],
    name: String,
    previewUrl: Option[String],
    trackNumber: Int,
    uri: String,
    isLocal: Boolean
)

object SimpleTrack {
  given decodeAlbumTrack: JsonDecoder[SimpleTrack] = DeriveJsonDecoder.gen[SimpleTrack]
}
