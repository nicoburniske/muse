package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import muse.domain.common.Types.UserId
import muse.domain.table.AccessLevel

import java.util.UUID

final case class ShareReview(
    reviewId: UUID,
    userId: UserId,
    @GQLDescription("If not specified user will have access revoked.")
    accessLevel: Option[AccessLevel])

final case class ShareReviewInput(input: ShareReview)
