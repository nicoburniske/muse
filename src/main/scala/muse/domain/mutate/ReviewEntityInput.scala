package muse.domain.mutate

import caliban.schema.Annotations.GQLInputName
import muse.domain.common.EntityType

import caliban.schema.{ArgBuilder, Schema}
@GQLInputName("ReviewEntityInput")
final case class ReviewEntityInput(entityType: EntityType, entityId: String) derives Schema.SemiAuto, ArgBuilder
