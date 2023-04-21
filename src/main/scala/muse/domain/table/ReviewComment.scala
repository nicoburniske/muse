package muse.domain.table

import muse.domain.common.Types.UserId
import zio.json.{DeriveJsonCodec, JsonCodec}

import java.time.Instant
import java.util.UUID

final case class ReviewComment(
    commentId: Long,
    createdAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
    reviewId: UUID,
    commenter: UserId,
    // Comment can be null if deleted.
    comment: Option[String]
)

