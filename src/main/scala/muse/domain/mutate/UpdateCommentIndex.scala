package muse.domain.mutate
import java.util.UUID

case class UpdateCommentIndex(commentId: Int, reviewId: UUID, index: Int)
