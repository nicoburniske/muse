package muse.domain.mutate

import java.util.UUID

import caliban.schema.{ArgBuilder, Schema}
final case class DeleteComment(reviewId: UUID, commentId: Long)
final case class DeleteCommentInput(input: DeleteComment)
