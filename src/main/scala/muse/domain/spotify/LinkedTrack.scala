package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class LinkedTrack(
    linkedFrom: Option[LinkedTrack],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    `type`: String,
    uri: String)

object LinkedTrack {
  given linkedTrackDecoder: JsonDecoder[LinkedTrack] = DeriveJsonDecoder.gen[LinkedTrack]
}
