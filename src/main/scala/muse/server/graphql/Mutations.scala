package muse.server.graphql

import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.{Comment, Review}
import muse.service.persist.DatabaseQueries
import zio.{IO, ZIO}

import java.sql.SQLException

type MutationEnv = Auth[UserSession] & DatabaseQueries

// TODO: add sharing.
case class Mutations(
    createReview: CreateReview => ZIO[MutationEnv, Throwable, Review],
    createComment: CreateComment => ZIO[MutationEnv, Throwable, Comment],
    updateReview: UpdateReview => ZIO[MutationEnv, Throwable, Boolean],
    updateComment: UpdateComment => ZIO[MutationEnv, Throwable, Boolean]
)

object Mutations {
  val live = Mutations(createReview, createComment, updateReview, updateComment)
}

def createReview(create: CreateReview) =
  for {
    user <- Auth.currentUser[UserSession]
    r    <- DatabaseQueries.createReview(user.id, create)
  } yield Review.fromTable(r)

def createComment(create: CreateComment) =
  for {
    user <- Auth.currentUser[UserSession]
    c    <- DatabaseQueries.createReviewComment(user.id, create)
  } yield Comment.fromTable(c)

def updateReview(update: UpdateReview) = for {
  user <- Auth.currentUser[UserSession]
  _    <- DatabaseQueries.updateReview(update)
} yield true

def updateComment(update: UpdateComment) = for {
  user <- Auth.currentUser[UserSession]
  _    <- DatabaseQueries.updateComment(update)
} yield true
