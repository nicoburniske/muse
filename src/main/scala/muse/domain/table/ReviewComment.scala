package muse.domain.table

import muse.domain.common.EntityType
import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.Instant
import java.util.UUID

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
    entityId: String
)

object ReviewComment {
  given reviewSummaryCodec: JsonCodec[ReviewComment] = DeriveJsonCodec.gen[ReviewComment]
}
