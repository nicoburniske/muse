package muse.domain.table

import caliban.schema.Annotations.GQLInputName
import muse.domain.common.EntityType

import java.util.UUID

@GQLInputName("UpdateReviewEntityInput")
final case class ReviewEntity(reviewId: UUID, entityType: EntityType, entityId: String)
