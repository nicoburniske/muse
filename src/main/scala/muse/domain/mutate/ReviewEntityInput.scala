package muse.domain.mutate

import caliban.schema.Annotations.GQLInputName
import muse.domain.common.EntityType

@GQLInputName("ReviewEntityInput")
final case class ReviewEntityInput(entityType: EntityType, entityId: String)
