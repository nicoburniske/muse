package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.table
import muse.server.graphql.Resolvers.{getEntity, getReviewComments}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    //    comments: Pagination => ZQuery[DatabaseQueries, Nothing, List[Comment]]
    comments: ZQuery[DatabaseOps, Throwable, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[SpotifyService, Throwable, ReviewEntity]
)

object Review {
  def fromTable(r: table.Review) =
    Review(
      r.id,
      r.createdAt,
      r.creatorId,
      r.reviewName,
      r.isPublic,
      getReviewComments(r.id),
      r.entityId,
      r.entityType,
      getEntity(r.entityId, r.entityType)
    )
}
