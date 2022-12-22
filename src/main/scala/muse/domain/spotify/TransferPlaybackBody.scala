package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
case class TransferPlaybackBody(deviceIds: List[String])

object TransferPlaybackBody {
  given encoder: JsonEncoder[TransferPlaybackBody] = DeriveJsonEncoder.gen[TransferPlaybackBody]
}
