package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.{BadRequest, Forbidden, InvalidEntity, InvalidUser, MuseError, Unauthorized}
import muse.domain.event.{CreatedComment, DeletedComment, ReviewUpdate, UpdatedComment}
import muse.domain.mutate.*
import muse.domain.session.UserSession
import muse.domain.spotify.{ErrorReason, ErrorResponse, StartPlaybackBody, UriOffset, PositionOffset as SpotifyPostionOffset}
import muse.domain.{spotify, table}
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyError, SpotifyService}
import muse.utils.Utils.*
import sttp.model.{Method, Uri}
import zio.{Hub, IO, Task, UIO, ZIO}

import java.sql.SQLException
import java.util.UUID

// TODO: add checking for what constitutes a valid comment. What does rating represent?
// TODO: What actually should return boolean? Deletion?
// TODO: Consider adding ZQuery for batched operations to DB.
type MutationEnv   = RequestSession[UserSession] & DatabaseService & RequestSession[SpotifyService] & Hub[ReviewUpdate]
type MutationError = Throwable | MuseError

final case class Mutations(
    createReview: Input[CreateReview] => ZIO[MutationEnv, MutationError, Review],
    createComment: Input[CreateComment] => ZIO[MutationEnv, MutationError, Comment],
    linkReviews: Input[table.ReviewLink] => ZIO[MutationEnv, MutationError, Boolean],
    updateReview: Input[UpdateReview] => ZIO[MutationEnv, MutationError, Review],
    updateReviewEntity: Input[table.ReviewEntity] => ZIO[MutationEnv, MutationError, Review],
    updateComment: Input[UpdateComment] => ZIO[MutationEnv, MutationError, Comment],
    deleteReview: Input[DeleteReview] => ZIO[MutationEnv, MutationError, Boolean],
    deleteComment: Input[DeleteComment] => ZIO[MutationEnv, MutationError, Boolean],
    deleteReviewLink: Input[table.ReviewLink] => ZIO[MutationEnv, MutationError, Boolean],
    shareReview: Input[ShareReview] => ZIO[MutationEnv, MutationError, Boolean],
    // Consider separating these mutations out.
    play: Input[Play] => ZIO[MutationEnv, MutationError, Boolean],
    playTracks: Input[PlayTracks] => ZIO[MutationEnv, MutationError, Boolean],
    playOffsetContext: Input[PlayOffsetContext] => ZIO[MutationEnv, MutationError, Boolean],
    playEntityContext: Input[PlayEntityContext] => ZIO[MutationEnv, MutationError, Boolean],
    seekPlayback: Input[SeekPlayback] => ZIO[MutationEnv, MutationError, Boolean],
    pausePlayback: AlterPlayback => ZIO[MutationEnv, MutationError, Boolean],
    skipToNext: AlterPlayback => ZIO[MutationEnv, MutationError, Boolean],
    skipToPrevious: AlterPlayback => ZIO[MutationEnv, MutationError, Boolean],
    toggleShuffle: Input[Boolean] => ZIO[MutationEnv, MutationError, Boolean],
    saveTracks: Input[List[String]] => ZIO[MutationEnv, MutationError, Boolean],
    removeSavedTracks: Input[List[String]] => ZIO[MutationEnv, MutationError, Boolean]
)

object Mutations {

  val live = Mutations(
    i => createReview(i.input),
    i => createComment(i.input),
    i => linkReviews(i.input),
    i => updateReview(i.input),
    i => updateReviewEntity(i.input),
    i => updateComment(i.input),
    i => deleteReview(i.input),
    i => deleteComment(i.input),
    i => deleteReviewLink(i.input),
    i => shareReview(i.input),
    i => play(i.input),
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

  type Mutation[A] = ZIO[MutationEnv, MutationError, A]

  def createReview(create: CreateReview) = for {
    user       <- RequestSession.get[UserSession].map(_.userId)
    r          <- DatabaseService.createReview(user, create)
    _          <- ZIO.logInfo(s"Successfully created review! ${r.reviewName}")
    maybeEntity = create.entity.map(e => table.ReviewEntity(r.id, e.entityType, e.entityId))
    _          <- ZIO.fromOption(maybeEntity).flatMap(updateReviewEntityFromTable).orElse(ZIO.logInfo("No entity included!"))
    _          <- ZIO
                    .fromOption(create.link).flatMap { l =>
                      DatabaseService.linkReviews(table.ReviewLink(l.parentReviewId, r.id)) *>
                        ZIO.logInfo(s"Successfully linked review ${r.id} to ${l.parentReviewId}")
                    }.orElse(ZIO.logInfo("No link included!"))
  } yield Review.fromTable(r, maybeEntity)

  private def updateReviewEntityFromTable(r: table.ReviewEntity) =
    DatabaseService
      .updateReviewEntity(r).fold(
        sqlException => ZIO.logError(s"Failed to update review entity $r! ${sqlException.getMessage}"),
        _ => ZIO.logInfo(s"Successfully updated review entity! ${r.reviewId}")
      ).unit

  def createComment(create: CreateComment) = for {
    user      <- RequestSession.get[UserSession]
    _         <- ZIO
                   .fail(BadRequest(Some("Comment must have a non-empty body")))
                   .when(create.comment.isEmpty)
    _         <- ZIO.foreachPar(create.entities)(e => validateEntity(e._2, e._1)) <&> validateCommentPermissions(
                   user.userId,
                   create.reviewId)
    result    <- DatabaseService.createReviewComment(user.userId, create)
    comment    = Comment.fromTable(result._1, result._2)
    published <- ZIO.serviceWithZIO[Hub[ReviewUpdate]](_.publish(CreatedComment(comment)))
    _         <- ZIO.logError("Failed to publish comment creation").unless(published)
  } yield comment

  def linkReviews(link: table.ReviewLink) = for {
    user   <- RequestSession.get[UserSession]
    _      <- ZIO.fail(BadRequest(Some("Can't link a review to itself"))).when(link.parentReviewId == link.childReviewId)
    _      <- validateReviewPermissions(user.userId, link.parentReviewId)
    result <- DatabaseService.linkReviews(link)
  } yield result

  def updateReview(update: UpdateReview) = for {
    user    <- RequestSession.get[UserSession]
    _       <- validateReviewPermissions(user.userId, update.reviewId)
    review  <- DatabaseService.updateReview(update)
    details <- DatabaseService.getReviewEntity(update.reviewId)
  } yield Review.fromTable(review, details)

  def updateReviewEntity(update: table.ReviewEntity) = for {
    user        <- RequestSession.get[UserSession]
    _           <- validateReviewPermissions(user.userId, update.reviewId)
    maybeReview <- DatabaseService.getReview(update.reviewId) <&> updateReviewEntityFromTable(update)
    review      <- ZIO.fromOption(maybeReview).orElseFail(BadRequest(Some("Review not found.")))
  } yield Review.fromTable(review, Some(update))

  def updateComment(update: UpdateComment) = for {
    user                  <- RequestSession.get[UserSession]
    _                     <- ZIO
                               .fail(BadRequest(Some("Comment must have a body.")))
                               .when(update.comment.exists(_.isEmpty))
    _                     <- validateCommentEditingPermissions(user.userId, update.reviewId, update.commentId)
    bothResults           <- DatabaseService.updateComment(update) <&> DatabaseService.getComment(update.commentId)
    (result, maybeComment) = bothResults
    comment                = Comment.fromTable(result, maybeComment.fold(Nil)(_._2))
    published             <- ZIO.serviceWithZIO[Hub[ReviewUpdate]](_.publish(UpdatedComment(comment)))
    _                     <- ZIO.logError("Failed to publish comment update").unless(published)
  } yield comment

  def deleteComment(d: DeleteComment): Mutation[Boolean] = for {
    user      <- RequestSession.get[UserSession]
    _         <- validateCommentEditingPermissions(user.userId, d.reviewId, d.commentId)
    result    <- DatabaseService.deleteComment(d)
    published <- ZIO.serviceWithZIO[Hub[ReviewUpdate]](_.publish(DeletedComment(d.reviewId, d.commentId)))
    _         <- ZIO.logError("Failed to publish comment deletion").unless(published)
  } yield result

  def deleteReview(d: DeleteReview): Mutation[Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.userId, d.id)
    result <- DatabaseService.deleteReview(d)
  } yield result

  def deleteReviewLink(link: table.ReviewLink) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.userId, link.parentReviewId)
    result <- DatabaseService.deleteReviewLink(link)
  } yield result

  def shareReview(s: ShareReview): ZIO[MutationEnv, MutationError | InvalidUser, Boolean] = for {
    userId <- RequestSession.get[UserSession].map(_.userId)
    _      <- ZIO.fail(InvalidUser("You cannot share a review you own with yourself")).when(userId == s.userId)
    _      <- validateReviewPermissions(userId, s.reviewId) <&> validateUser(s.userId)
    result <- DatabaseService.shareReview(s)
  } yield result

  def play(play: Play) = for {
      spotify <- RequestSession.get[SpotifyService]
      res <- spotify.startPlayback(play.deviceId, None)
    } yield res

  def playTracks(play: PlayTracks) = for {
    spotify <- RequestSession.get[SpotifyService]
    uris     = play.trackIds.map(toUri(EntityType.Track, _))
    body     = StartPlaybackBody(None, Some(uris), None, play.positionMs)
    res     <- spotify.startPlayback(play.deviceId, Some(body))
  } yield res

  def playOffsetContext(play: PlayOffsetContext) = for {
    spotifyService <- RequestSession.get[SpotifyService]
    offset = spotify.PositionOffset(play.offset.position)
    contextUri = toUri(play.offset.context)
    body = StartPlaybackBody(Some(contextUri),None, Some(offset), play.positionMs)
    res <- spotifyService.startPlayback(play.deviceId, Some(body))
  } yield res

  def playEntityContext(play: PlayEntityContext) = for {
    spotify <- RequestSession.get[SpotifyService]
    outerUri = toUri(play.offset.outer)
    innerUri = toUri(play.offset.inner)
    body = StartPlaybackBody(Some(outerUri), None, Some(UriOffset(innerUri)), play.positionMs)
    res <- spotify.startPlayback(play.deviceId, Some(body))
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
    RequestSession.get[SpotifyService].flatMap(_.saveTracks(trackIds.toVector)).addTimeLog("Tracks saved")

  def removeSavedTracks(trackIds: List[String]) =
    RequestSession.get[SpotifyService].flatMap(_.removeSavedTracks(trackIds.toVector)).addTimeLog("Removed tracks")

  private def toUri(e: Context): String = toUri(e.entityType, e.entityId)
  private def toUri(entityType: EntityType, entityId: String) = entityType match
    case EntityType.Album    => s"spotify:album:$entityId"
    case EntityType.Artist   => s"spotify:artist:$entityId"
    case EntityType.Track    => s"spotify:track:$entityId"
    case EntityType.Playlist => s"spotify:playlist:$entityId"

  private def validateEntity(
      entityId: String,
      entityType: EntityType): ZIO[RequestSession[SpotifyService], Throwable | InvalidEntity, Unit] =
    RequestSession.get[SpotifyService].flatMap(_.isValidEntity(entityId, entityType)).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(InvalidEntity(entityId, entityType))
    }

  private def validateReviewPermissions(userId: String, reviewId: UUID): ZIO[DatabaseService, Throwable | Forbidden, Unit] =
    DatabaseService.canModifyReview(userId, reviewId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User $userId cannot modify review $reviewId"))
    }

  private def validateCommentEditingPermissions(
      userId: String,
      reviewId: UUID,
      commentId: Int): ZIO[DatabaseService, Throwable | Forbidden, Unit] =
    DatabaseService.canModifyComment(userId, reviewId, commentId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User $userId cannot modify comment $commentId"))
    }

  private def validateCommentPermissions(userId: String, reviewId: UUID): ZIO[DatabaseService, Throwable | Forbidden, Unit] = {
    DatabaseService.canMakeComment(userId, reviewId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User $userId cannot comment on review $reviewId"))
    }
  }

  private def validateUser(userId: String): ZIO[DatabaseService, Throwable | InvalidUser, Unit] =
    DatabaseService.getUserById(userId).flatMap {
      case Some(_) => ZIO.unit
      case None    => ZIO.fail(InvalidUser(userId))
    }
}
