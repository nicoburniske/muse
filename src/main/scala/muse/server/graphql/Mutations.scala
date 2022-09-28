package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, InvalidEntity, InvalidUser}
import muse.domain.mutate.{CreateComment, CreateReview, DeleteComment, DeleteReview, ShareReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.{IO, ZIO}

import java.sql.SQLException
import java.util.UUID

type MutationEnv = RequestSession[UserSession] & DatabaseService & SpotifyService

// TODO: add un-sharing.
// TODO: add checking for what constitutes a valid comment. What does rating represent?
// TODO: What actually should return boolean? Deletion?
// TODO: Consider adding ZQuery for batched operations to DB.
final case class Mutations(
    createReview: Input[CreateReview] => ZIO[MutationEnv, Throwable, Review],
    createComment: Input[CreateComment] => ZIO[MutationEnv, Throwable, Comment],
    updateReview: Input[UpdateReview] => ZIO[MutationEnv, Throwable, Boolean],
    updateComment: Input[UpdateComment] => ZIO[MutationEnv, Throwable, Boolean],
    deleteReview: Input[DeleteReview] => ZIO[MutationEnv, Throwable, Boolean],
    deleteComment: Input[DeleteComment] => ZIO[MutationEnv, Throwable, Boolean],
    shareReview: Input[ShareReview] => ZIO[MutationEnv, Throwable, Boolean]
)

final case class Input[T](input: T)

// TODO: Consider trying to make Scoped layer or something?
case class MutationsLive(user: UserSession) {}

object Mutations {

  val live = Mutations(
    i => createReview(i.input),
    i => createComment(i.input),
    i => updateReview(i.input),
    i => updateComment(i.input),
    i => deleteReview(i.input),
    i => deleteComment(i.input),
    i => shareReview(i.input)
  )

  def createReview(create: CreateReview) = for {
    user <- RequestSession.get[UserSession]
    _    <- validateEntity(create.entityId, create.entityType)
    r    <- DatabaseService.createReview(user.id, create)
  } yield Review.fromTable(r)

  def createComment(create: CreateComment) = for {
    user <- RequestSession.get[UserSession]
    _    <- validateEntity(create.entityId, create.entityType) <&>
              validateCommentPermissions(user.id, create.reviewId)
    c    <- DatabaseService.createReviewComment(user.id, create)
  } yield Comment.fromTable(c)

  def updateReview(update: UpdateReview) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, update.reviewId)
    result <- DatabaseService.updateReview(update)
  } yield result

  def updateComment(update: UpdateComment) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateCommentEditingPermissions(user.id, update.reviewId, update.commentId)
    result <- DatabaseService.updateComment(update)
  } yield result

  def deleteComment(d: DeleteComment) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateCommentEditingPermissions(user.id, d.reviewId, d.commentId)
    result <- DatabaseService.deleteComment(d)
  } yield result

  def deleteReview(d: DeleteReview) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, d.id)
    result <- DatabaseService.deleteReview(d)
  } yield result

  def shareReview(s: ShareReview) = for {
    user   <- RequestSession.get[UserSession]
    _      <- validateReviewPermissions(user.id, s.reviewId) <&> validateUser(s.userId)
    result <- DatabaseService.shareReview(s)
  } yield result

  private def validateEntity(entityId: String, entityType: EntityType): ZIO[SpotifyService, Throwable, Unit] =
    SpotifyService.isValidEntity(entityId, entityType).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(InvalidEntity(entityId, entityType))
    }

  private def validateReviewPermissions(userId: String, reviewId: UUID) =
    DatabaseService.canModifyReview(userId, reviewId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User ${userId} cannot modify review ${reviewId}"))
    }

  private def validateCommentEditingPermissions(userId: String, reviewId: UUID, commentId: Int) =
    DatabaseService.canModifyComment(userId, reviewId, commentId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User ${userId} cannot modify comment ${commentId}"))
    }

  private def validateCommentPermissions(userId: String, reviewId: UUID) = {
    DatabaseService.canMakeComment(userId, reviewId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User ${userId} cannot comment on review ${reviewId}"))
    }
  }

  private def validateUser(userId: String) =
    DatabaseService.getUserById(userId).flatMap {
      case Some(_) => ZIO.unit
      case None    => ZIO.fail(InvalidUser(userId))
    }
}
