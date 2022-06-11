package domain.tables

import java.util.UUID
import java.time.Instant

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    isPublic: Boolean,
    entityType: Int,
    entityId: String
)
