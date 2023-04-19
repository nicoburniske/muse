package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import muse.domain.table.AccessLevel

import java.util.UUID

final case class ShareReview(
    reviewId: UUID,
    userId: String,
    @GQLDescription("If not specified user will have access revoked.")
    accessLevel: Option[AccessLevel])

final case class ShareReviewInput(input: ShareReview)
