package muse.domain.spotify

import zio.json.*

case class PlaybackDeviceIds(@jsonField("device_ids") deviceIds: List[String])

object PlaybackDeviceIds {
  given encoder: JsonEncoder[PlaybackDeviceIds] = DeriveJsonEncoder.gen[PlaybackDeviceIds]
}
