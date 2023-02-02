package muse.domain.mutate

import zio.json.*

import java.util.UUID

final case class InitialLink(parentReviewId: UUID)
final case class CreateReview(name: String, isPublic: Boolean, entity: Option[ReviewEntityInput], link: Option[InitialLink])
