package muse.server.graphql.subgraph

import muse.domain.spotify.{PlaybackContext, PlaybackDevice}

case class PlaybackState(
    device: PlaybackDevice,
    shuffleState: Boolean,
    repeatState: String,
    timestamp: Long,
    context: Option[PlaybackContext],
    progressMs: Long,
    item: Option[Track],
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
      playbackState.item.map(t => Track.fromSpotifySimple(t, None)),
      playbackState.currentlyPlayingType,
      playbackState.isPlaying
    )
}
