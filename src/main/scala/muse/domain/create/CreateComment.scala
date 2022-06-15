package muse.domain.create

import java.util.UUID

import muse.domain.common.EntityType

final case class CreateComment(
    reviewID: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    comment: Option[String],
    rating: Option[Int],
    entityType: EntityType,
    entityId: Int
)
