package muse.domain.mutate

import java.util.UUID

case class UpdateReviewLink(parentReviewId: UUID, childReviewId: UUID, linkIndex: Int)
