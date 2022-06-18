package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

final case class Track(
    album: Option[Album],
    artists: List[Artist],
    @jsonField("available_markets")
    availableMarkets: Option[List[String]],
    @jsonField("disc_number")
    discNumber: Int,
    @jsonField("duration_ms")
    durationMs: Int,
    explicit: Boolean,
    @jsonField("external_ids")
    externalIds: Option[Map[String, String]],
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    href: String,
    id: String,
    @jsonField("is_playable")
    isPlayable: Option[Boolean],
    @jsonField("linked_from")
    linkedFrom: Option[LinkedTrack],
//    restrictions: Option[Restrictions],
    name: String,
    popularity: Option[Int],
    @jsonField("preview_url")
    previewUrl: Option[String],
    @jsonField("track_number")
    trackNumber: Int,
    @jsonField("type")
    `type`: String,
    uri: String,
    @jsonField("is_local")
    isLocal: Boolean
) extends Entity(id, EntityType.Track)

object Track {
  given decodeTrack: JsonCodec[Track] = DeriveJsonCodec.gen[Track]
}
