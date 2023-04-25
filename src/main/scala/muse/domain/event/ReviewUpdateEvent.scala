package muse.domain.event

import muse.domain.table

import java.util.UUID

sealed trait ReviewUpdateEvent:
  def reviewId: UUID = this match
    case CreatedComment(comment, _, _, _) => comment.reviewId
    case UpdatedComment(comment, _, _, _) => comment.reviewId
    case DeletedComment(reviewId, _)      => reviewId

final case class CreatedComment(
    commetn: table.ReviewComment,
    index: table.ReviewCommentIndex,
    parentChild: List[table.ReviewCommentParentChild],
    entities: List[table.ReviewCommentEntity])
    extends ReviewUpdateEvent

final case class UpdatedComment(
    comment: table.ReviewComment,
    index: table.ReviewCommentIndex,
    parentChild: List[table.ReviewCommentParentChild],
    entities: List[table.ReviewCommentEntity])
    extends ReviewUpdateEvent

object UpdatedComment {
  def fromTableRows(
      comments: List[(
          table.ReviewComment,
          table.ReviewCommentIndex,
          Option[table.ReviewCommentParentChild],
          Option[table.ReviewCommentEntity])]) = {
    val grouped = comments.groupBy(_._1.commentId)
    grouped.map { (_, comments) =>
      val comment     = comments.map(_._1).head
      val index       = comments.head._2
      val parentChild = comments
        .filter {
          case (_, _, pc, _) => pc.exists(pc => pc.parentCommentId == comment.commentId || pc.childCommentId == comment.commentId)
        }.map(_._3).flatten
      val entities    = comments.map(_._4).flatten
      UpdatedComment(comment, index, parentChild, entities)
    }
  }
}

// TODO: how to remove override?
final case class DeletedComment(override val reviewId: UUID, commentId: Long) extends ReviewUpdateEvent
