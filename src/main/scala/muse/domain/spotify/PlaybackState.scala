package muse.domain.spotify

import muse.domain.common.Entity
import zio.json.*

case class PlaybackState(
    device: PlaybackDevice,
    @jsonField("shuffle_state")
    shuffleState: Boolean,
    @jsonField("repeat_state")
    repeatState: String,
    timestamp: Long,
    context: Option[PlaybackContext],
    @jsonField("progress_ms")
    progressMs: Long,
    // This can be null sometimes?
    item: Option[Track],
    @jsonField("currently_playing_type")
    currentlyPlayingType: String,
    @jsonField("is_playing")
    isPlaying: Boolean
    // TODO: incorporate actions?
)

object PlaybackState:
  given decoder: JsonDecoder[PlaybackState]          = DeriveJsonDecoder.gen[PlaybackState]
  given decoderContext: JsonDecoder[PlaybackContext] = DeriveJsonDecoder.gen[PlaybackContext]

case class PlaybackContext(
    @jsonField("uri")
    uri: String,
    @jsonField("metadata")
    metadata: Option[Map[String, String]],
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    @jsonField("href")
    href: String,
    @jsonField("type")
    `type`: String)
