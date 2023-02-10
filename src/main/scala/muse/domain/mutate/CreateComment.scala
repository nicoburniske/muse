package muse.domain.mutate

import caliban.schema.Annotations.GQLInputName
import muse.domain.common.EntityType
import zio.json.*

import java.util.UUID

final case class CreateComment(
    reviewId: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Long],
    comment: String,
    entities: List[ReviewEntityInput],
    // If none, will be added to the end of the list.
    commentIndex: Option[Int]
)
