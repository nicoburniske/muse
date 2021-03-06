package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

import java.util.UUID

final case class UpdateComment(
    commentId: Int,
    reviewId: UUID,
    comment: Option[String],
    rating: Option[Int]
)
