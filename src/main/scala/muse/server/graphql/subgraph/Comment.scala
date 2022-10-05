package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.table.ReviewComment
import muse.server.graphql.resolver.{GetEntity, GetUser}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

final case class Comment(
    id: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenterId: String,
    commenter: ZQuery[DatabaseService & RequestSession[SpotifyService], Throwable, User],
    comment: Option[String],
    rating: Option[Int],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[RequestSession[SpotifyService], Throwable, ReviewEntity]
)

object Comment {
  def fromTable(r: ReviewComment) = Comment(
    r.id,
    r.reviewId,
    r.createdAt,
    r.updatedAt,
    r.parentCommentId,
    r.commenter,
    GetUser.queryByUserId(r.commenter),
    r.comment,
    r.rating,
    r.entityId,
    r.entityType,
    GetEntity.query(r.entityId, r.entityType)
  )
}
