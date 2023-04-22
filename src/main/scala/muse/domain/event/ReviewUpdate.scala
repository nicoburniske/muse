package muse.domain.event

import caliban.schema.Annotations.{GQLDescription, GQLInterface}
import muse.server.graphql.subgraph.Comment

import java.util.UUID

// TODO: Review Updates and Deletions.
//@GQLInterface // for some reason this isn't working?
sealed trait ReviewUpdate:
  def reviewId: UUID

object ReviewUpdate {
  case class CreatedComment(comment: Comment) extends ReviewUpdate:
    override val reviewId = comment.reviewId

  case class UpdatedComment(comment: Comment) extends ReviewUpdate:
    override val reviewId = comment.reviewId

  case class DeletedComment(reviewId: UUID, commentId: Long) extends ReviewUpdate
}
