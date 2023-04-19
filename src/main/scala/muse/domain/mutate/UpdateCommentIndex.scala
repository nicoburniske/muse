package muse.domain.mutate
import java.util.UUID
import caliban.schema.{ArgBuilder, Schema}

final case class UpdateCommentIndex(commentId: Int, reviewId: UUID, index: Int)
final case class UpdateCommentIndexInput(input:  UpdateCommentIndex)
