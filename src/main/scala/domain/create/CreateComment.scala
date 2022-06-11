package domain.create

import java.util.UUID

final case class CreateComment(
    reviewID: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenter: String,
    comment: Option[String],
    rating: Option[Int],
    entityType: Int,
    entityId: Int
)
