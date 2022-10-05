package muse.server.graphql.subgraph

import muse.domain.spotify.{PlaybackContext, PlaybackDevice}

case class PlaybackState(
    device: PlaybackDevice,
    shuffleState: Boolean,
    repeatState: String,
    timestamp: Long,
    context: PlaybackContext,
    progressMs: Long,
    item: Track,
    currentlyPlayingType: String,
    isPlaying: Boolean
)

object PlaybackState {
  def apply(playbackState: muse.domain.spotify.PlaybackState): PlaybackState =
    PlaybackState(
      playbackState.device,
      playbackState.shuffleState,
      playbackState.repeatState,
      playbackState.timestamp,
      playbackState.context,
      playbackState.progressMs,
      Track.fromSpotify(playbackState.item, None),
      playbackState.currentlyPlayingType,
      playbackState.isPlaying
    )
}
