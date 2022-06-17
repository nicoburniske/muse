package muse.domain.create

import java.util.UUID
import zio.json.*
import muse.domain.common.EntityType

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
  given reviewSummaryDecoder: JsonDecoder[CreateComment] = DeriveJsonDecoder.gen[CreateComment]

  given reviewSummaryEncoder: JsonEncoder[CreateComment] = DeriveJsonEncoder.gen[CreateComment]
}
