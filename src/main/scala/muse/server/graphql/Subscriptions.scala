package muse.server.graphql

import muse.domain.spotify.PlaybackDevice
import muse.server.graphql.subgraph.PlaybackState
import muse.service.spotify.SpotifyService
import zio.*
import zio.stream.ZStream

final case class Subscriptions(
    nowPlaying: ZStream[SpotifyService, Throwable, PlaybackState],
    availableDevices: ZStream[SpotifyService, Throwable, List[PlaybackDevice]]
)

object Subscriptions {
  val live: Subscriptions = Subscriptions(playbackState, availableDevices)

  lazy val playbackState =
    ZStream
      .tick(5.seconds)
      .mapZIO(_ => SpotifyService.currentPlaybackState)
      .map(PlaybackState(_))

  lazy val availableDevices =
    ZStream
      .tick(5.seconds)
      .mapZIO(_ => SpotifyService.getAvailableDevices)
      .map(_.toList)
      // If devices are the same don't send update.
      .mapAccum(List.empty[PlaybackDevice]) { (devices, newDevices) =>
        if (devices.toSet == newDevices.toSet)
          devices    -> List.empty
        else
          newDevices -> newDevices
      }.filter(_.nonEmpty)
}
