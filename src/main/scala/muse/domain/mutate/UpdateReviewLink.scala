package muse.domain.mutate

import java.util.UUID
import caliban.schema.{ArgBuilder, Schema}

final case class UpdateReviewLink(parentReviewId: UUID, childReviewId: UUID, linkIndex: Int)
final case class UpdateReviewLinkInput(input: UpdateReviewLink)
