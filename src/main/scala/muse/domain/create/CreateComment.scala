package muse.domain.create

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
  given reviewSummaryDecoder: JsonDecoder[CreateComment] = DeriveJsonDecoder.gen[CreateComment]

  given reviewSummaryEncoder: JsonEncoder[CreateComment] = DeriveJsonEncoder.gen[CreateComment]
}
