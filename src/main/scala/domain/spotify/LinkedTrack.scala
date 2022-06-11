package domain.spotify

import zio.json.*

final case class LinkedTrack(
    @jsonField("linked_from")
    linkedFrom: Option[LinkedTrack],
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    href: String,
    id: String,
    @jsonField("type")
    `type`: String,
    uri: String)

object LinkedTrack {
  given linkedTrackDecoder: JsonDecoder[LinkedTrack] = DeriveJsonDecoder.gen[LinkedTrack]
}
