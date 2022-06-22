package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.service.persist.DatabaseQueries
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
    commenter: ZQuery[DatabaseQueries, Throwable, User],
    comment: Option[String],
    rating: Option[Int],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[SpotifyService, Throwable, ReviewEntity]
)
