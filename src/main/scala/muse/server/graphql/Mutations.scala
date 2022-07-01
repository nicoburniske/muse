package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, InvalidEntity}
import muse.domain.mutate.{
  CreateComment,
  CreateReview,
  DeleteComment,
  DeleteReview,
  UpdateComment,
  UpdateReview
}
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.{IO, ZIO}

import java.sql.SQLException
import java.util.UUID

type MutationEnv = Auth[UserSession] & DatabaseOps & SpotifyService

// TODO: add sharing.
// TODO: add checking for what constitutes a valid comment. What does rating represent?
final case class Mutations(
    createReview: Input[CreateReview] => ZIO[MutationEnv, Throwable, Review],
    createComment: Input[CreateComment] => ZIO[MutationEnv, Throwable, Comment],
    updateReview: Input[UpdateReview] => ZIO[MutationEnv, Throwable, Boolean],
    updateComment: Input[UpdateComment] => ZIO[MutationEnv, Throwable, Boolean],
    deleteReview: Input[DeleteReview] => ZIO[MutationEnv, Throwable, Boolean],
    deleteComment: Input[DeleteComment] => ZIO[MutationEnv, Throwable, Boolean]
)

final case class Input[T](input: T)

object Mutations {
  val live = Mutations(
    i => createReview(i.input),
    i => createComment(i.input),
    i => updateReview(i.input),
    i => updateComment(i.input),
    i => deleteReview(i.input),
    i => deleteComment(i.input)
  )

  def createReview(create: CreateReview) =
    for {
      _    <- validateEntity(create.entityId, create.entityType)
      user <- Auth.currentUser[UserSession]
      r    <- DatabaseOps.createReview(user.id, create)
    } yield Review.fromTable(r)

  def createComment(create: CreateComment) =
    for {
      user <- Auth.currentUser[UserSession]
      _    <- validateEntity(create.entityId, create.entityType)
      _    <- DatabaseOps.canMakeComment(user.id, create.reviewId).flatMap {
                case true  => ZIO.succeed(())
                case false => ZIO.fail(Forbidden(s"User ${user.id} cannot comment on review ${create.reviewId}"))
              }
      c    <- DatabaseOps.createReviewComment(user.id, create)
    } yield Comment.fromTable(c)

  def updateReview(update: UpdateReview) = for {
    user <- Auth.currentUser[UserSession]
    _    <- validateReviewPermissions(user.id, update.reviewId)
    _    <- DatabaseOps.updateReview(update)
  } yield true

  def updateComment(update: UpdateComment) = for {
    user <- Auth.currentUser[UserSession]
    _    <- validateCommentPermissions(user.id, update.reviewId, update.commentId)
    _    <- DatabaseOps.updateComment(update)
  } yield true

  def deleteComment(d: DeleteComment) = for {
    user   <- Auth.currentUser[UserSession]
    _      <- validateCommentPermissions(user.id, d.reviewId, d.commentId)
    result <- DatabaseOps.deleteComment(d)
  } yield result

  def deleteReview(d: DeleteReview) = for {
    user   <- Auth.currentUser[UserSession]
    _      <- validateReviewPermissions(user.id, d.id)
    result <- DatabaseOps.deleteReview(d)
  } yield result

  private def validateEntity(entityId: String, entityType: EntityType): ZIO[SpotifyService, Throwable, Unit] =
    SpotifyService.isValidEntity(entityId, entityType).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(InvalidEntity(entityId, entityType))
    }

  private def validateReviewPermissions(userId: String, reviewId: UUID) = {
    DatabaseOps.canModifyReview(userId, reviewId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User ${userId} cannot modify review ${reviewId}"))
    }
  }

  private def validateCommentPermissions(userId: String, reviewId: UUID, commentId: Int) =
    DatabaseOps.canModifyComment(userId, reviewId, commentId).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(Forbidden(s"User ${userId} cannot modify comment ${commentId}"))
    }
}
