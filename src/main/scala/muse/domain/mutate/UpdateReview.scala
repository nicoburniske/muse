package muse.domain.mutate

import zio.json.*

import java.util.UUID

case class UpdateReview(reviewId: UUID, name: String, isPublic: Boolean)

object UpdateReview {
  given updateReviewCodec: JsonCodec[UpdateReview] = DeriveJsonCodec.gen[UpdateReview]
}
