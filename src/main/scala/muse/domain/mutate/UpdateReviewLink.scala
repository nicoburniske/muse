package muse.domain.mutate

import java.util.UUID

final case class UpdateReviewLink(parentReviewId: UUID, childReviewId: UUID, linkIndex: Int)
final case class UpdateReviewLinkInput(input: UpdateReviewLink)
