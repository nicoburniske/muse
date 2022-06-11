package domain.create

import domain.common.EntityType
import java.util.UUID

final case class CreateComment(
    reviewID: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenter: String,
    comment: Option[String],
    rating: Option[Int],
    entityType: EntityType,
    entityId: Int
)
