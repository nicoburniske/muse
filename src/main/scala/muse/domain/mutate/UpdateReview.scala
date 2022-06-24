package muse.domain.mutate

import zio.json.*

import java.util.UUID

final case class UpdateReview(reviewId: UUID, name: String, isPublic: Boolean)
