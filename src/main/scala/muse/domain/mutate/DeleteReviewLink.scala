package muse.domain.mutate

import java.util.UUID

import caliban.schema.{ArgBuilder, Schema}
final case class DeleteReviewLink(parentReviewId: UUID, childReviewId: UUID) 
final case class DeleteReviewLinkInput(input: DeleteReviewLink)

