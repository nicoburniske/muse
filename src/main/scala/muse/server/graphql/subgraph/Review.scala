package muse.server.graphql.subgraph

import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, Unauthorized}
import muse.domain.session.UserSession
import muse.domain.table
import muse.domain.table.AccessLevel
import muse.server.graphql.resolver.{GetCollaborators, GetEntity, GetReviewComments, GetUser}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

import java.time.Instant
import java.util.UUID

// TODO: Add list of collaborators
final case class Review(
    id: UUID,
    createdAt: Instant,
    creator: ZQuery[DatabaseService & RequestSession[UserSession], Unauthorized, User],
    reviewName: String,
    isPublic: Boolean,
    comments: ZQuery[DatabaseService, Throwable, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[RequestSession[SpotifyService], Nothing, ReviewEntity],
    // TODO: this can be forbidden.
    collaborators: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Collaborator]]
)

case class Collaborator(user: User, accessLevel: AccessLevel)

object Review {
  def fromTable(r: table.Review) = {
    val collaborators = for {
      reviewAccess <- GetCollaborators.query(r.id)
      user         <- ZQuery.fromZIO(RequestSession.get[UserSession]).map(_.userId)
      _            <- if (r.creatorId == user || reviewAccess.exists(_.userId == user)) ZQuery.unit
                      else ZQuery.fail(Forbidden("You are not allowed to view this review"))
      subQueries    = reviewAccess.map { reviewAccess =>
                        GetUser.queryByUserId(reviewAccess.userId).map(user => Collaborator(user, reviewAccess.accessLevel))
                      }
      allUsers     <- ZQuery.collectAll(subQueries)
    } yield allUsers

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
      GetEntity.query(r.entityId, r.entityType).orDie,
      collaborators
    )
  }
}
