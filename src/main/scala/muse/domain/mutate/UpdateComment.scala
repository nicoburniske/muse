package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

final case class UpdateComment(
    commentId: Int,
    comment: Option[String],
    rating: Option[Int]
)
