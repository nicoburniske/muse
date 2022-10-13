package muse.domain.spotify
import zio.json.*

case class StartPlaybackBody(
    @jsonField("context_uri")
    contextUri: Option[String],
    uris: Option[List[String]],
    offset: Option[PlaybackOffset],
    @jsonField("position_ms")
    positionMs: Option[Int])

object StartPlaybackBody {
  given encoder: JsonEncoder[StartPlaybackBody] = DeriveJsonEncoder.gen[StartPlaybackBody]
}
