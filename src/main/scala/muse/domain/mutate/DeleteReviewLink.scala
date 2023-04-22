package muse.domain.mutate

import java.util.UUID
final case class DeleteReviewLink(parentReviewId: UUID, childReviewId: UUID)
final case class DeleteReviewLinkInput(input: DeleteReviewLink)
