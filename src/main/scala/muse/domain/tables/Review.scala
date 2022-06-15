package muse.domain.tables

import muse.domain.common.EntityType

import java.util.UUID
import java.time.Instant

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    entityType: EntityType,
    entityId: String
)
