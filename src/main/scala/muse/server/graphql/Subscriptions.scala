package muse.server.graphql

import muse.domain.spotify.{PlaybackDevice, Track}
import muse.server.graphql.subgraph.PlaybackState
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils.*
import zio.*
import zio.stream.ZStream

final case class Subscriptions(
    nowPlaying: NowPlayingArgs => ZStream[RequestSession[SpotifyService], Throwable, PlaybackState],
    availableDevices: ZStream[RequestSession[SpotifyService], Throwable, List[PlaybackDevice]]
)

case class NowPlayingArgs(tickInterval: Int)

object Subscriptions {
  val live: Subscriptions = Subscriptions(a => playbackState(a.tickInterval), availableDevices)

  def playbackState(tickInterval: Int) =
    val tick = if (tickInterval < 1) 1 else tickInterval
    ZStream
      .tick(tick.seconds)
      .mapZIO(_ => RequestSession.get[SpotifyService])
      .mapZIO(_.currentPlaybackState.addTimeLog("Retrieved current playback"))
      .filter(_.isDefined)
      .map(_.get)
      .map(PlaybackState(_))
      .tapErrorCause(cause => ZIO.logErrorCause(s"Error while getting playback state: $cause", cause))

  lazy val availableDevices =
    ZStream
      .tick(5.seconds)
      .mapZIO(_ => RequestSession.get[SpotifyService])
      .mapZIO(_.getAvailableDevices)
      .map(_.toList)
      // If devices are the same don't send update.
      .mapAccum(List.empty[PlaybackDevice]) { (devices, newDevices) =>
        if (devices.toSet == newDevices.toSet)
          devices    -> List.empty
        else
          newDevices -> newDevices
      }.filter(_.nonEmpty)
      .tap(newDevices => ZIO.logInfo(s"Found new devices: $newDevices"))
}
