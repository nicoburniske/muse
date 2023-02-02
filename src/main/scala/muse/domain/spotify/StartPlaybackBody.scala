package muse.domain.spotify
import zio.json.*

@jsonMemberNames(SnakeCase)
case class StartPlaybackBody(
    contextUri: Option[String],
    uris: Option[List[String]],
    offset: Option[PlaybackOffset],
    positionMs: Option[Int])

object StartPlaybackBody {
  given encoder: JsonEncoder[StartPlaybackBody] = DeriveJsonEncoder.gen[StartPlaybackBody]
}
