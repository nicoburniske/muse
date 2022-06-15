package muse.domain.create

import muse.domain.common.EntityType

final case class CreateReview(name: String, isPublic: Boolean, entityType: EntityType, entityId: String)
