package muse.domain.table

import muse.domain.common.EntityType
import muse.domain.common.Types.UserId
import zio.json.*

import java.time.Instant
import java.util.UUID

final case class Review(
    reviewId: UUID,
    createdAt: Instant,
    creatorId: UserId,
    reviewName: String,
    isPublic: Boolean
)
