package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.{BadRequest, Forbidden, InvalidEntity, InvalidUser, MuseError, Unauthorized}
import muse.domain.mutate.{Context, CreateComment, CreateReview, DeleteComment, DeleteReview, EntityOffset, PositionOffset, ShareReview, StartPlayback, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.domain.spotify.{ErrorReason, ErrorResponse, StartPlaybackBody, UriOffset, PositionOffset as SpotifyPostionOffset}
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyError, SpotifyService}
import muse.utils.Utils.*
import sttp.model.{Method, Uri}
import zio.{IO, Task, UIO, ZIO}

import java.sql.SQLException
import java.util.UUID

type MutationEnv = RequestSession[UserSession] & DatabaseService & SpotifyService

// TODO: add un-sharing.
// TODO: add checking for what constitutes a valid comment. What does rating represent?
// TODO: What actually should return boolean? Deletion?
// TODO: Consider adding ZQuery for batched operations to DB.

type MutationError = Throwable | MuseError

final case class Mutations(
    createReview: Input[CreateReview] => ZIO[MutationEnv, MutationError, Review],
    createComment: Input[CreateComment] => ZIO[MutationEnv, MutationError, Comment],
    updateReview: Input[UpdateReview] => ZIO[MutationEnv, MutationError, Boolean],
    updateComment: Input[UpdateComment] => ZIO[MutationEnv, MutationError, Boolean],
    deleteReview: Input[DeleteReview] => ZIO[MutationEnv, MutationError, Boolean],
    deleteComment: Input[DeleteComment] => ZIO[MutationEnv, MutationError, Boolean],
    shareReview: Input[ShareReview] => ZIO[MutationEnv, MutationError, Boolean],
    startPlayback: Input[StartPlayback] => ZIO[MutationEnv, MutationError, Boolean],
    saveTracks: Input[List[String]] => ZIO[MutationEnv, MutationError, Boolean]
)

final case class Input[T](input: T)

object Mutations {

  val live = Mutations(
    i => createReview(i.input),
    i => createComment(i.input),
    i => updateReview(i.input),
    i => updateComment(i.input),
    i => deleteReview(i.input),
    i => deleteComment(i.input),
    i => shareReview(i.input),
    i => startPlayback(i.input),
    i => saveTracks(i.input)
  )

  type Mutation[A] = ZIO[MutationEnv, MutationError, A]

  def createReview(create: CreateReview): Mutation[Review] = for {
    user <- RequestSession.get[UserSession]
    _    <- validateEntity(create.entityId, create.entityType)
    r    <- DatabaseService.createReview(user.id, create)
  } yield Review.fromTable(r)

  def createComment(create: CreateComment): Mutation[Comment] = for {
    user <- RequestSession.get[UserSession]
    _    <- ZIO
              .fail(BadRequest(Some("Comment must have a body or rating")))
              .unless(create.comment.exists(_.nonEmpty) || create.rating.isDefined)
    _    <- validateEntity(create.entityId, create.entityType) <&> validateCommentPermissions(user.id, create.reviewId)
    c    <- DatabaseService.createReviewComment(user.id, create)
  } yield Comment.fromTable(c)

  def updateReview(update: UpdateReview): Mutation[Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, update.reviewId)
    result <- DatabaseService.updateReview(update)
  } yield result

  def updateComment(update: UpdateComment): Mutation[Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- ZIO
                .fail(BadRequest(Some("Comment must have a body or rating")))
                .unless(update.comment.exists(_.nonEmpty) || update.rating.isDefined)
    _      <- validateCommentEditingPermissions(user.id, update.reviewId, update.commentId)
    result <- DatabaseService.updateComment(update)
  } yield result

  def deleteComment(d: DeleteComment): Mutation[Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateCommentEditingPermissions(user.id, d.reviewId, d.commentId)
    result <- DatabaseService.deleteComment(d)
  } yield result

  def deleteReview(d: DeleteReview): Mutation[Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, d.id)
    result <- DatabaseService.deleteReview(d)
  } yield result

  def shareReview(s: ShareReview): ZIO[MutationEnv, MutationError | InvalidUser, Boolean] = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, s.reviewId) <&> validateUser(s.userId)
    result <- DatabaseService.shareReview(s)
  } yield result

  def startPlayback(playback: StartPlayback) =
    val getBody       = (playback.positionOffset, playback.entityOffset) match
      case (Some(_), Some(_))                                                                   =>
        ZIO.fail(BadRequest(Some("Playback can only use one kind of offset (Position, Entity).")))
      case (None, Some(EntityOffset(Context(outerType, outerId), Context(innerType, innerId)))) =>
        val outerUri = Some(toUri(outerType, outerId))
        val innerUri = Some(UriOffset(toUri(innerType, innerId)))
        ZIO.succeed(StartPlaybackBody(outerUri, playback.uris, innerUri, playback.positionMs))
      case (Some(PositionOffset(Context(eType, id), position)), None)                           =>
        ZIO.succeed(
          StartPlaybackBody(Some(toUri(eType, id)), playback.uris, Some(SpotifyPostionOffset(position)), playback.positionMs))
      case (None, None)                                                                         =>
        ZIO.succeed(StartPlaybackBody(None, playback.uris, None, playback.positionMs))
    val startPlayback = getBody.flatMap(SpotifyService.startPlayback(None, _))
    playback
      .deviceId.fold(startPlayback) { deviceId =>
        SpotifyService
          .transferPlayback(deviceId).foldZIO(
            _ => ZIO.logInfo("Failed to transfer playback, starting playback on any device") *> startPlayback,
            _ =>
              ZIO.logInfo("Succeeded transferring playback") *>
                getBody.flatMap(SpotifyService.startPlayback(Some(deviceId), _))
          )
      }.addTimeLog("Playback started")

  def saveTracks(trackIds: List[String]) =
    SpotifyService.saveTracks(trackIds.toVector).addTimeLog("Tracks saved")

  private def toUri(entityType: EntityType, entityId: String) = entityType match
    case EntityType.Album    => s"spotify:album:$entityId"
    case EntityType.Artist   => s"spotify:artist:$entityId"
    case EntityType.Track    => s"spotify:track:$entityId"
    case EntityType.Playlist => s"spotify:playlist:$entityId"

  private def validateEntity(entityId: String, entityType: EntityType): ZIO[SpotifyService, Throwable | InvalidEntity, Unit] =
    SpotifyService.isValidEntity(entityId, entityType).flatMap {
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
