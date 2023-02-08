package muse.domain.mutate

import java.util.UUID

final case class DeleteComment(reviewId: UUID, commentId: Long)
