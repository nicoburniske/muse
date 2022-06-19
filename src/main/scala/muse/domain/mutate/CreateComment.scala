package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

import java.util.UUID

final case class CreateComment(
    reviewID: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    comment: Option[String],
    rating: Option[Int],
    entityType: EntityType,
    entityId: String
)

object CreateComment {
  given reviewSummaryCodec: JsonCodec[CreateComment] = DeriveJsonCodec.gen[CreateComment]
}
