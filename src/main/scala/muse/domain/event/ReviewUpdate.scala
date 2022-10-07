package muse.domain.event

import muse.domain.table.ReviewComment

import java.util.UUID

// TODO: Review Updates and Deletions.
sealed trait ReviewUpdate:
  def reviewId: UUID

case class CreatedComment(comment: ReviewComment) extends ReviewUpdate:
  override val reviewId = comment.reviewId

case class UpdatedComment(comment: ReviewComment) extends ReviewUpdate:
  override val reviewId = comment.reviewId

case class DeletedComment(reviewId: UUID, commentId: Int) extends ReviewUpdate