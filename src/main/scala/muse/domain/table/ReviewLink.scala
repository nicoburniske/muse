package muse.domain.table

import java.util.UUID

case class ReviewLink(parentReviewId: UUID, childReviewId: UUID)