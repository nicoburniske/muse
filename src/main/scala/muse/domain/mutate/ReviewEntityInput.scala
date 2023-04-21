package muse.domain.mutate

import caliban.schema.Annotations.GQLInputName
import caliban.schema.{ArgBuilder, Schema}
import muse.domain.common.EntityType

final case class ReviewEntityInput(entityType: EntityType, entityId: String)
