package muse.domain.event

import caliban.schema.Annotations.GQLInterface
import muse.server.graphql.subgraph.Comment

import java.util.UUID

// TODO: Review Updates and Deletions.
//@GQLInterface // for some reason this isn't working?
sealed trait ReviewUpdate:
  def reviewId: UUID

case class CreatedComment(comment: Comment) extends ReviewUpdate:
  override val reviewId = comment.reviewId

case class UpdatedComment(comment: Comment) extends ReviewUpdate:
  override val reviewId = comment.reviewId

case class UpdatedCommentIndex(comments: List[Comment]) extends ReviewUpdate:
  override val reviewId = comments.head.reviewId

case class DeletedComment(reviewId: UUID, commentId: Int) extends ReviewUpdate
