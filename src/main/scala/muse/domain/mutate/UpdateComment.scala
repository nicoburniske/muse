package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

import java.util.UUID

final case class UpdateComment(
    commentId: Long,
    reviewId: UUID,
    comment: String
)
