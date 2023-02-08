package muse.domain.table

import muse.domain.common.EntityType
import zio.json.*

import java.time.Instant
import java.util.UUID

final case class Review(
    reviewId: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean
)

object Review {
  given reviewSummaryCodec: JsonCodec[Review] = DeriveJsonCodec.gen[Review]
}
