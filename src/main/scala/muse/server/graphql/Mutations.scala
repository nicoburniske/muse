package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyService
import zio.{IO, ZIO}

import java.sql.SQLException

type MutationEnv = Auth[UserSession] & DatabaseQueries & SpotifyService

// TODO: add sharing.
case class Mutations(
    createReview: CreateReview => ZIO[MutationEnv, Throwable, Review],
    createComment: CreateComment => ZIO[MutationEnv, Throwable, Comment],
    updateReview: UpdateReview => ZIO[MutationEnv, Throwable, Boolean],
    updateComment: UpdateComment => ZIO[MutationEnv, Throwable, Boolean]
)

object Mutations {
  val live = Mutations(createReview, createComment, updateReview, updateComment)

  def createReview(create: CreateReview) =
    for {
      _    <- ensureValidEntity(create.entityId, create.entityType)
      user <- Auth.currentUser[UserSession]
      r    <- DatabaseQueries.createReview(user.id, create)
    } yield Review.fromTable(r)

  def createComment(create: CreateComment) =
    for {
      _    <- ensureValidEntity(create.entityId, create.entityType)
      user <- Auth.currentUser[UserSession]
      c    <- DatabaseQueries.createReviewComment(user.id, create)
    } yield Comment.fromTable(c)

  // TODO: check if permissions are valid.
  def updateReview(update: UpdateReview) = for {
    user <- Auth.currentUser[UserSession]
    _    <- DatabaseQueries.updateReview(update)
  } yield true

  def updateComment(update: UpdateComment) = for {
    user <- Auth.currentUser[UserSession]
    _    <- DatabaseQueries.updateComment(update)
  } yield true

  private def ensureValidEntity(
      entityId: String,
      entityType: EntityType): ZIO[SpotifyService, Throwable, Unit] =
    SpotifyService.isValidEntity(entityId, entityType).flatMap { isValid =>
      if (isValid)
        ZIO.succeed(())
      else
        ZIO.fail(InvalidEntity(entityId, entityType))
    }
}
