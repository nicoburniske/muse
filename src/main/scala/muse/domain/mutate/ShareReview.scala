package muse.domain.mutate

import muse.domain.table.AccessLevel

import java.util.UUID

case class ShareReview(reviewId: UUID, userId: String, access: AccessLevel)
