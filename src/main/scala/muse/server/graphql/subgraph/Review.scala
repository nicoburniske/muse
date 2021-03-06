package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.table
import muse.server.graphql.resolver.{GetEntity, GetReviewComments, GetUser}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

// TODO: Add list of collaborators
final case class Review(
    id: UUID,
    createdAt: Instant,
    creator: ZQuery[DatabaseOps, Nothing, User],
    reviewName: String,
    isPublic: Boolean,
    comments: ZQuery[DatabaseOps, Throwable, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[SpotifyService, Nothing, ReviewEntity]
)

object Review {
  def fromTable(r: table.Review) =
    Review(
      r.id,
      r.createdAt,
      GetUser.queryByUserId(r.creatorId),
      r.reviewName,
      r.isPublic,
      GetReviewComments.query(r.id),
      r.entityId,
      r.entityType,
      // TODO: ensure this is ok
      GetEntity.query(r.entityId, r.entityType).orDie
    )
}
