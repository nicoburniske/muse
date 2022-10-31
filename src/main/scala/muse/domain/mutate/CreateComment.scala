package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

import java.util.UUID

final case class CommentEntity(entityType: EntityType, entityId: String)

final case class CreateComment(
    reviewId: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    comment: Option[String],
    rating: Option[Int],
    entities: List[CommentEntity]
)
