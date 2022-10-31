package muse.domain.table

import java.util.UUID

case class ReviewLink(parentReview: UUID, childReview: UUID)