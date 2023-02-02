package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
case class PlaybackDevice(
    id: String,
    isActive: Boolean,
    isPrivateSession: Boolean,
    isRestricted: Boolean,
    name: String,
    `type`: String,
    volumePercent: Int)

object PlaybackDevice {
  given decoder: JsonDecoder[PlaybackDevice] = DeriveJsonDecoder.gen[PlaybackDevice]
}
