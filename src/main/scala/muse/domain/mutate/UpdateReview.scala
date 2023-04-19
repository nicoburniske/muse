package muse.domain.mutate

import zio.json.*

import java.util.UUID

import caliban.schema.{ArgBuilder, Schema}

final case class UpdateReview(reviewId: UUID, name: String, isPublic: Boolean)
final case class UpdateReviewInput(input: UpdateReview)
