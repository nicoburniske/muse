package muse.domain.create

import muse.domain.common.EntityType
import zio.json.*

final case class CreateReview(name: String, isPublic: Boolean, entityType: EntityType, entityId: String)

object CreateReview {
  given reviewSummaryCodec: JsonCodec[CreateReview] = DeriveJsonCodec.gen[CreateReview]

}
