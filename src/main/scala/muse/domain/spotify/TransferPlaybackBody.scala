package muse.domain.spotify

import zio.json.*

case class TransferPlaybackBody(@jsonField("device_ids") deviceIds: List[String])

object TransferPlaybackBody {
  given encoder: JsonEncoder[TransferPlaybackBody] = DeriveJsonEncoder.gen[TransferPlaybackBody]
}
