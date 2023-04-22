package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class PlaybackState(
    device: PlaybackDevice,
    shuffleState: Boolean,
    repeatState: String,
    timestamp: Long,
    context: Option[PlaybackContext],
    progressMs: Long,
    // This can be null sometimes?
    item: Option[SimpleTrack],
    currentlyPlayingType: String,
    isPlaying: Boolean
    // TODO: incorporate actions?
)

object PlaybackState:
  given decoder: JsonDecoder[PlaybackState]          = DeriveJsonDecoder.gen[PlaybackState]
  given decoderContext: JsonDecoder[PlaybackContext] = DeriveJsonDecoder.gen[PlaybackContext]

@jsonMemberNames(SnakeCase)
final case class PlaybackContext(
    uri: String,
    metadata: Option[Map[String, String]],
    externalUrls: Map[String, String],
    href: String,
    `type`: String)
