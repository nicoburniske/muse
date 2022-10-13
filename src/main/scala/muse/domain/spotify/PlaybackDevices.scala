package muse.domain.spotify

import zio.json.*

case class PlaybackDevices(devices: Vector[PlaybackDevice])

object PlaybackDevices {
  given decoder: JsonDecoder[PlaybackDevices] = DeriveJsonDecoder.gen[PlaybackDevices]
}
