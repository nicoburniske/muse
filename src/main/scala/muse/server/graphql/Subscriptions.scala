package muse.server.graphql

import muse.domain.event.{CreatedComment, DeletedComment, UpdatedComment}
import muse.domain.session.UserSession
import muse.domain.spotify.{PlaybackDevice, Track, PlaybackState as SpotPlaybackState}
import muse.server.graphql.resolver.GetPlaylistTracks
import muse.server.graphql.subgraph.{Comment, PlaybackState, PlaylistTrack, ReviewUpdate}
import muse.service.UserSessionService
import muse.service.event.ReviewUpdateService
import muse.service.spotify.SpotifyService
import muse.utils.Utils.*
import zio.*
import zio.stream.{ZPipeline, ZStream}

import java.util.UUID

final case class Subscriptions(
    nowPlaying: NowPlayingInput => ZStream[Subscriptions.Env, Throwable, PlaybackState],
    availableDevices: ZStream[Subscriptions.Env, Throwable, List[PlaybackDevice]],
    reviewUpdates: ReviewUpdatesInput => ZStream[Subscriptions.Env, Throwable, ReviewUpdate]
)

case class NowPlayingInput(tickInterval: Int)
case class ReviewUpdatesInput(reviewIds: Set[UUID])

object Subscriptions {
  type Env = ReviewUpdateService & SpotifyService & UserSession
  val live: Subscriptions = Subscriptions(
    a => playbackState(a.tickInterval),
    availableDevices,
    i => reviewUpdates(i.reviewIds)
  )

  // noinspection InfallibleEffectRecoveryInspection
  def playbackState(tickInterval: Int) =
    val tick = if (tickInterval < 1) 1 else tickInterval
    ZStream
      .tick(500.millis)
      .via(getSpotifyPipeline)
      .mapZIO(_.currentPlaybackState.retry(Schedule.recurs(3) && Schedule.exponential(1.second)))
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

  // noinspection InfallibleEffectRecoveryInspection
  lazy val availableDevices =
    ZStream
      .tick(5.seconds)
      .via(getSpotifyPipeline)
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
      .tapErrorCause(cause => ZIO.logErrorCause(s"Error while getting availableDevices: $cause", cause))

  // TODO: have to filter out reviews that the user doesn't have access to.
  def reviewUpdates(reviewIds: Set[UUID]) = for {
    service <- ZStream.service[ReviewUpdateService]
    events  <- ZStream.scoped(service.subscribe(reviewIds))
    event   <- events
  } yield event match
    case CreatedComment(r, index, parentChild, entities) =>
      ReviewUpdate.CreatedComment(Comment.fromTable(r, index, parentChild, entities))
    case UpdatedComment(r, index, parentChild, entities) =>
      ReviewUpdate.UpdatedComment(Comment.fromTable(r, index, parentChild, entities))
    case DeletedComment(reviewId, commentId)             =>
      ReviewUpdate.DeletedComment(reviewId, commentId)

  private def getSpotifyPipeline = ZPipeline.mapZIO(_ => ZIO.service[SpotifyService])

  private def flattenOption[T] =
    ZPipeline.filter[Option[T]](_.isDefined) >>>
      ZPipeline.map[Option[T], T](_.get)
}
