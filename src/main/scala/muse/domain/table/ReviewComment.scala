package muse.domain.table

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.Instant
import java.util.UUID

final case class ReviewComment(
    commentId: Long,
    createdAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
    reviewId: UUID,
    commenter: String,
    // Comment can be null if deleted.
    comment: Option[String]
)

object ReviewComment {
  given reviewSummaryCodec: JsonCodec[ReviewComment] = DeriveJsonCodec.gen[ReviewComment]
}
