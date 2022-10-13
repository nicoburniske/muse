package muse.domain.spotify

import zio.json.*

case class PlaybackDevice(
    id: String,
    @jsonField("is_active")
    isActive: Boolean,
    @jsonField("is_private_session")
    isPrivateSession: Boolean,
    @jsonField("is_restricted")
    isRestricted: Boolean,
    name: String,
    @jsonField("type")
    `type`: String,
    @jsonField("volume_percent")
    volumePercent: Int)

object PlaybackDevice {
  given decoder: JsonDecoder[PlaybackDevice] = DeriveJsonDecoder.gen[PlaybackDevice]
}
