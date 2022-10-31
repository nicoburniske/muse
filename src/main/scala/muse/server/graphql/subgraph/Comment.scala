package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.session.UserSession
import muse.domain.table
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
    commenter: ZQuery[DatabaseService & RequestSession[SpotifyService] & RequestSession[UserSession], Throwable, User],
    comment: Option[String],
    entities: ZQuery[RequestSession[SpotifyService], Throwable, List[ReviewEntity]]
)

object Comment {
  def fromTable(r: table.ReviewComment, entities: List[table.ReviewCommentEntity]) = Comment(
    r.id,
    r.reviewId,
    r.createdAt,
    r.updatedAt,
    r.parentCommentId,
    GetUser.queryByUserId(r.commenter),
    r.comment,
    ZQuery.foreachPar(entities)(e =>  GetEntity.query(e.entityId, e.entityType))
  )
}
