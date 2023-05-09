package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.table
import muse.server.graphql.resolver.{GetComment, GetEntity, GetReviewComments, GetUser}
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

final case class Comment(
    id: Long,
    commentIndex: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
    // If none, then it is root comment.
    parentCommentId: Option[Long],
    childCommentIds: List[Long],
    commenter: User,
    comment: Option[String],
    entities: ZQuery[SpotifyService, Throwable, List[ReviewEntity]],
    parentComment: ZQuery[GetComment.Env, Throwable, Option[Comment]],
    childComments: ZQuery[GetComment.Env, Throwable, List[Comment]],
    allChildComments: ZQuery[GetComment.Env, Throwable, List[Comment]]
)

object Comment {
  def fromTable(
      r: table.ReviewComment,
      index: table.ReviewCommentIndex,
      parentChild: List[table.ReviewCommentParentChild],
      entities: List[table.ReviewCommentEntity]
  ) =
    val parentId = parentChild.map(_.parentCommentId).find(_ != r.commentId)
    val childIds = parentChild.filter(_.parentCommentId == r.commentId).map(_.childCommentId)
    Comment(
      r.commentId,
      index.commentIndex,
      r.reviewId,
      r.createdAt,
      r.updatedAt,
      r.deleted,
      parentId,
      childIds,
      GetUser.queryByUserId(r.commenter),
      r.comment,
      ZQuery.foreachPar(entities)(e => GetEntity.query(e.entityId, e.entityType)),
      parentId.fold(ZQuery.succeed(None))(p => GetComment.query(p)),
      ZQuery.foreachPar(childIds)(GetComment.query).map(_.flatten),
      GetComment.queryAllChildren(r.commentId)
    )

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
      Comment.fromTable(comment, index, parentChild, entities)
    }
  }

}
