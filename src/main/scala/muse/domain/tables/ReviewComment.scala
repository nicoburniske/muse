package muse.domain.tables

import muse.domain.common.EntityType

import java.util.UUID
import java.time.Instant

final case class ReviewComment(
    // GUID?
    id: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenter: String,
    comment: Option[String],
    rating: Option[Int],
    entityType: EntityType,
    entityId: Int
)
