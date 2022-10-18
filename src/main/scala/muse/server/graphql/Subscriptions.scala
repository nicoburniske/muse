package muse.server.graphql

import muse.domain.event.{CreatedComment, ReviewUpdate}
import muse.domain.session.UserSession
import muse.domain.spotify.{PlaybackDevice, Track, PlaybackState as SpotPlaybackState}
import muse.server.graphql.resolver.GetPlaylistTracks
import muse.server.graphql.subgraph.{PlaybackState, PlaylistTrack}
import muse.service.spotify.SpotifyService
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils.*
import zio.*
import zio.stream.{ZPipeline, ZStream}

import java.util.UUID

type Sessions = UserSessions & RequestSession[SpotifyService] & RequestSession[UserSession]
final case class Subscriptions(
    nowPlaying: NowPlayingArgs => ZStream[Sessions, Throwable, PlaybackState],
    availableDevices: ZStream[Sessions, Throwable, List[PlaybackDevice]],
    playlistTracks: Input[GetPlaylistTracks] => ZStream[Sessions, Throwable, PlaylistTrack],
    reviewUpdates: ReviewUpdatesArgs => ZStream[Sessions & Hub[ReviewUpdate] & Scope, Throwable, ReviewUpdate]
)

case class NowPlayingArgs(tickInterval: Int)
case class ReviewUpdatesArgs(reviewId: UUID)

object Subscriptions {
  val live: Subscriptions = Subscriptions(
    a => playbackState(a.tickInterval),
    availableDevices,
    i => GetPlaylistTracks.stream(i.input.playlistId, i.input.numTracks),
    i => reviewUpdates(i.reviewId)
  )

  // noinspection InfallibleEffectRecoveryInspection
  def playbackState(tickInterval: Int) =
    val tick = if (tickInterval < 1) 1 else tickInterval
    ZStream
      .tick(tick.seconds)
      .via(getSpotify)
      .mapZIO(_.currentPlaybackState)
      .via(flattenOption)
      // Only send updates for new playback states.
      .mapAccum(Option.empty[SpotPlaybackState]) {
        case (None, curr)                             =>
          Some(curr) -> Some(curr)
        case (old @ Some(prev), curr) if prev == curr =>
          old -> None
        case (_, curr)                                =>
          Some(curr) -> Some(curr)
      }
      .via(flattenOption)
      .map(PlaybackState(_))
      .tapErrorCause(cause => ZIO.logErrorCause(s"Error while getting playback state: $cause", cause))

  lazy val availableDevices =
    ZStream
      .tick(5.seconds)
      .via(getSpotify)
      .mapZIO(_.getAvailableDevices)
      .map(_.toList)
      // If devices are the same don't send update.
      .mapAccum(List.empty[PlaybackDevice]) { (devices, newDevices) =>
        if (devices.toSet == newDevices.toSet)
          devices    -> List.empty
        else
          newDevices -> newDevices
      }
      .filter(_.nonEmpty)

  def reviewUpdates(reviewId: UUID) = for {
    queue  <- ZStream.fromZIO(ZIO.serviceWithZIO[Hub[ReviewUpdate]](_.subscribe))
    update <- ZStream.fromQueue(queue).filter(_.reviewId == reviewId)
  } yield update
  // TODO: Add user session references to see who is viewing review live.
//    .ensuring(
//    )

  private def getSpotify = ZPipeline.mapZIO(_ => ZIO.serviceWithZIO[RequestSession[SpotifyService]](_.get))

  private def flattenOption[T] =
    ZPipeline.filter[Option[T]](_.isDefined) >>>
      ZPipeline.map[Option[T], T](_.get)
}
