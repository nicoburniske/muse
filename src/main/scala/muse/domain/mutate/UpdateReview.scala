package muse.domain.mutate

import caliban.schema.{ArgBuilder, Schema}
import zio.json.*

import java.util.UUID

final case class UpdateReview(reviewId: UUID, name: String, isPublic: Boolean)
final case class UpdateReviewInput(input: UpdateReview)
