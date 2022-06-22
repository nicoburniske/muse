package muse.domain.mutate

import zio.json.*

import java.util.UUID

case class UpdateReview(reviewId: UUID, name: String, isPublic: Boolean)
