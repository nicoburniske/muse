package muse.domain.table

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.Instant
import java.util.UUID

final case class ReviewComment(
    // GUID?
    id: Int,
    commentIndex: Int,
    createdAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    reviewId: UUID,
    commenter: String,
    // Comment can be null if deleted.
    comment: Option[String]
)

object ReviewComment {
  given reviewSummaryCodec: JsonCodec[ReviewComment] = DeriveJsonCodec.gen[ReviewComment]
}
