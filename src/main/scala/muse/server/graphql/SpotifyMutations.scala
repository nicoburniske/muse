package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.*
import muse.domain.mutate.*
import muse.domain.session.UserSession
import muse.domain.spotify.{
  ErrorReason,
  ErrorResponse,
  StartPlaybackBody,
  TransferPlaybackBody,
  UriOffset,
  PositionOffset as SpotifyPostionOffset
}
import muse.server.graphql.subgraph.ReviewUpdate
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.{Hub, ZIO}

type SpotifyMutationEnv = RequestSession[UserSession] & RequestSession[SpotifyService]
case class SpotifyMutations(
    play: PlayInput => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    transferPlayback: Input[TransferPlayback] => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    playTracks: PlayTracksInput => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    playOffsetContext: PlayOffsetContextInput => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    playEntityContext: PlayEntityContextInput => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    seekPlayback: Input[SeekPlayback] => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    pausePlayback: AlterPlayback => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    skipToNext: AlterPlayback => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    skipToPrevious: AlterPlayback => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    toggleShuffle: Input[Boolean] => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    saveTracks: Input[List[String]] => ZIO[SpotifyMutationEnv, Throwable, Boolean],
    removeSavedTracks: Input[List[String]] => ZIO[SpotifyMutationEnv, Throwable, Boolean]
)

object SpotifyMutations {
  val live = SpotifyMutations(
    i => play(i.input),
    i => transferPlayback(i.input),
    i => playTracks(i.input),
    i => playOffsetContext(i.input),
    i => playEntityContext(i.input),
    i => seekPlayback(i.input),
    i => pausePlayback(i.deviceId),
    i => skipToNext(i.deviceId),
    i => skipToPrevious(i.deviceId),
    i => toggleShuffle(i.input),
    i => saveTracks(i.input),
    i => removeSavedTracks(i.input)
  )

  def play(play: Play) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.startPlayback(play.deviceId, None)
  } yield res

  def transferPlayback(transferPlayback: TransferPlayback) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.transferPlayback(transferPlayback.deviceId)
  } yield res

  def playTracks(play: PlayTracks) = for {
    spotify <- RequestSession.get[SpotifyService]
    uris     = play.trackIds.map(toUri(EntityType.Track, _))
    body     = StartPlaybackBody(None, Some(uris), None, play.positionMs)
    res     <- spotify.startPlayback(play.deviceId, Some(body))
  } yield res

  def playOffsetContext(play: PlayOffsetContext) = for {
    spotifyService <- RequestSession.get[SpotifyService]
    offset          = SpotifyPostionOffset(play.offset.position)
    contextUri      = toUri(play.offset.context)
    body            = StartPlaybackBody(Some(contextUri), None, Some(offset), play.positionMs)
    res            <- spotifyService.startPlayback(play.deviceId, Some(body))
  } yield res

  def playEntityContext(play: PlayEntityContext) = for {
    spotify <- RequestSession.get[SpotifyService]
    outerUri = toUri(play.offset.outer)
    innerUri = toUri(play.offset.inner)
    body     = StartPlaybackBody(Some(outerUri), None, Some(UriOffset(innerUri)), play.positionMs)
    res     <- spotify.startPlayback(play.deviceId, Some(body))
  } yield res

  def seekPlayback(playback: SeekPlayback) = for {
    _       <- ZIO.fail(BadRequest(Some("Playback offset cannot be negative"))).when(playback.positionMs < 0)
    _       <- ZIO.logInfo(s"Seeking playback to ${playback.positionMs}ms")
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.seekPlayback(playback.deviceId, playback.positionMs)
  } yield res

  def pausePlayback(deviceId: Option[String]) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.pausePlayback(deviceId)
  } yield res

  def skipToNext(deviceId: Option[String]) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.skipToNext(deviceId)
  } yield res

  def skipToPrevious(deviceId: Option[String]) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.skipToPrevious(deviceId)
  } yield res

  def toggleShuffle(shuffleState: Boolean) = for {
    spotify <- RequestSession.get[SpotifyService]
    res     <- spotify.toggleShuffle(shuffleState)
  } yield res

  def saveTracks(trackIds: List[String]) =
    RequestSession.get[SpotifyService].flatMap(_.saveTracks(trackIds.toVector))

  def removeSavedTracks(trackIds: List[String]) =
    RequestSession.get[SpotifyService].flatMap(_.removeSavedTracks(trackIds.toVector))

  private def toUri(e: Context): String                       = toUri(e.entityType, e.entityId)
  private def toUri(entityType: EntityType, entityId: String) = entityType match
    case EntityType.Album    => s"spotify:album:$entityId"
    case EntityType.Artist   => s"spotify:artist:$entityId"
    case EntityType.Track    => s"spotify:track:$entityId"
    case EntityType.Playlist => s"spotify:playlist:$entityId"
}
