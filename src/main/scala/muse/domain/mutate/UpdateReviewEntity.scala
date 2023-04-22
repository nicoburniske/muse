package muse.domain.mutate

import muse.domain.common.EntityType
import muse.domain.table

import java.util.UUID

final case class UpdateReviewEntity(reviewId: UUID, entityType: EntityType, entityId: String) {
  def toTable = table.ReviewEntity(reviewId, entityType, entityId)
}
final case class UpdateReviewEntityInput(input: UpdateReviewEntity)
