package muse.domain.mutate
import caliban.schema.{ArgBuilder, Schema}

import java.util.UUID

final case class UpdateCommentIndex(commentId: Int, reviewId: UUID, index: Int)
final case class UpdateCommentIndexInput(input: UpdateCommentIndex)
