package muse.server.graphql.subgraph

import muse.domain.common.EntityType
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
