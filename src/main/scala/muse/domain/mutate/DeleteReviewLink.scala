package muse.domain.mutate

import java.util.UUID

case class DeleteReviewLink(parentReviewId: UUID, childReviewId: UUID)
