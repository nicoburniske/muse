package domain.tables

import domain.common.EntityType

import java.util.UUID
import java.time.Instant

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    isPublic: Boolean,
    entityType: EntityType,
    entityId: String
)
