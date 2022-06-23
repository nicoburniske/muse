package muse.domain.table

import muse.domain.common.EntityType
import zio.json.*

import java.time.Instant
import java.util.UUID

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    entityType: EntityType,
    entityId: String
)

object Review {
  given reviewSummaryCodec: JsonCodec[Review] = DeriveJsonCodec.gen[Review]
}
