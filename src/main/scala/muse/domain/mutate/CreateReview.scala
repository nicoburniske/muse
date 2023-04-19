package muse.domain.mutate

import zio.json.*

import caliban.schema.{ArgBuilder, Schema}

import java.util.UUID

final case class InitialLink(parentReviewId: UUID) derives Schema.SemiAuto, ArgBuilder
final case class CreateReview(name: String, isPublic: Boolean, entity: Option[ReviewEntityInput], link: Option[InitialLink])
final case class CreateReviewInput(input: CreateReview)
