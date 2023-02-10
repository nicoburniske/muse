package muse.domain.table

import java.util.UUID

final case class ReviewCommentIndex(reviewId: UUID, commentId: Long, commentIndex: Int, parentCommentId: Option[Long])
