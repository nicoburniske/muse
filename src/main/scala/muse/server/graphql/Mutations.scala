package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, InvalidEntity}
import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.{IO, ZIO}

import java.sql.SQLException

type MutationEnv = Auth[UserSession] & DatabaseOps & SpotifyService

// TODO: add sharing.
final case class Mutations(
    createReview: CreateReview => ZIO[MutationEnv, Throwable, Review],
    createComment: CreateComment => ZIO[MutationEnv, Throwable, Comment],
    updateReview: UpdateReview => ZIO[MutationEnv, Throwable, Boolean],
    updateComment: UpdateComment => ZIO[MutationEnv, Throwable, Boolean]
)

object Mutations {
  val live = Mutations(createReview, createComment, updateReview, updateComment)

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

  // TODO: check if permissions are valid.
  def updateReview(update: UpdateReview) = for {
    user <- Auth.currentUser[UserSession]
    _    <- validatePermissions(update, user)
    _    <- DatabaseOps.updateReview(update)
  } yield true

  def updateComment(update: UpdateComment) = for {
    user <- Auth.currentUser[UserSession]
    _    <- DatabaseOps.canModifyComment(user.id, update.commentId).flatMap {
              case true  => ZIO.succeed(())
              case false => ZIO.fail(Forbidden(s"User ${user.id} cannot modify comment ${update.commentId}"))
            }
    _    <- DatabaseOps.updateComment(update)
  } yield true

  private def validateEntity(entityId: String, entityType: EntityType): ZIO[SpotifyService, Throwable, Unit] =
    SpotifyService.isValidEntity(entityId, entityType).flatMap {
      case true  => ZIO.succeed(())
      case false => ZIO.fail(InvalidEntity(entityId, entityType))
    }

  private def validatePermissions(update: UpdateReview, user: UserSession) = {
    DatabaseOps.canModifyReview(user.id, update.reviewId).flatMap {
      case true  => ZIO.succeed(())
      case false => ZIO.fail(Forbidden(s"User ${user.id} cannot modify review ${update.reviewId}"))
    }
  }
}
