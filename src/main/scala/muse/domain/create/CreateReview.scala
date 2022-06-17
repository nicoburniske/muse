package muse.domain.create

import muse.domain.common.EntityType
import zio.json.*

final case class CreateReview(name: String, isPublic: Boolean, entityType: EntityType, entityId: String)

object CreateReview {
  given reviewSummaryDecoder: JsonDecoder[CreateReview] = DeriveJsonDecoder.gen[CreateReview]

  given reviewSummaryEncoder: JsonEncoder[CreateReview] = DeriveJsonEncoder.gen[CreateReview]
}
