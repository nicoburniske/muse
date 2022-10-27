package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import muse.domain.table.AccessLevel

import java.util.UUID

case class ShareReview(
    reviewId: UUID,
    userId: String,
    @GQLDescription("If not specified user will have access revoked.") accessLevel: Option[AccessLevel])
