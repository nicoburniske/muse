package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, Unauthorized}
import muse.domain.session.UserSession
import muse.domain.table
import muse.domain.table.AccessLevel
import muse.server.graphql.resolver.{GetChildReviews, GetCollaborators, GetEntity, GetReview, GetReviewComments, GetUser}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

final case class Review(
    id: UUID,
    createdAt: Instant,
    creator: User,
    reviewName: String,
    isPublic: Boolean,
    comments: ZQuery[DatabaseService, Throwable, List[Comment]],
    entity: ZQuery[RequestSession[SpotifyService], Throwable, Option[ReviewEntity]],
    childReviews: ZQuery[DatabaseService, Throwable, List[Review]],
    // TODO: this can be forbidden.
    collaborators: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Collaborator]]
)

case class Collaborator(
    user: User,
    accessLevel: AccessLevel,
    review: ZQuery[DatabaseService & RequestSession[UserSession], Throwable, Review])

object Collaborator {
  def fromTable(r: table.ReviewAccess) = Collaborator(
    GetUser.queryByUserId(r.userId),
    r.accessLevel,
    GetReview.query(r.reviewId).map(_.get)
  )
}

object Review {
  def fromTable(r: table.Review, entity: Option[table.ReviewEntity]) = {
    val maybeEntity = entity.fold(ZQuery.succeed(None))(r => GetEntity.query(r.entityId, r.entityType).map(Some(_)))

    Review(
      r.id,
      r.createdAt,
      GetUser.queryByUserId(r.creatorId),
      r.reviewName,
      r.isPublic,
      GetReviewComments.query(r.id),
      // This can't be 'orDie' because there are cases when:
      // People make playlists private.
      // Things are deleted from Spotify.
      maybeEntity,
      GetChildReviews.query(r.id),
      GetCollaborators.query(r.id)
    )
  }
}
