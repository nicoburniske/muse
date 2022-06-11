package domain.create

import domain.common.EntityType

final case class CreateReview(creatorId: String, isPublic: Boolean, entityType: EntityType, entityId: String)
