package muse.domain.table

import java.util.UUID

final case class ReviewLink(linkId: Int, linkIndex: Int, parentReviewId: UUID, childReviewId: UUID)
