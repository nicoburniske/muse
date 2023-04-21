package muse.domain.mutate

import zio.json.*

import java.util.UUID

final case class CreateReviewInput(input: CreateReview)
final case class CreateReview(name: String, isPublic: Boolean, entity: Option[ReviewEntityInput], link: Option[InitialLink])
final case class InitialLink(parentReviewId: UUID)
