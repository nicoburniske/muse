package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.tables
import muse.server.graphql.Resolvers.{getEntity, getReviewComments}
import muse.service.persist.DatabaseQueries
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
    comments: ZQuery[DatabaseQueries, Throwable, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[SpotifyService, Throwable, ReviewEntity]
)

object Review {
  def fromTable(r: tables.Review) =
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
