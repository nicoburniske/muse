package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import muse.domain.common.EntityType
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.Pagination.Default
import muse.server.graphql.resolver.{GetReview, GetSearch, GetUser}
import muse.server.graphql.subgraph.{Review, SearchResult, User}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.util.UUID

final case class UserArgs(id: Option[String])

final case class ReviewsArgs(id: UUID)

final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])

// TODO: Integrate "Input" for arguments.
final case class Queries(
    user: UserArgs => ZQuery[Auth[UserSession] & DatabaseOps, Throwable, User],
    review: ReviewsArgs => ZQuery[DatabaseOps, Throwable, Option[Review]],
    search: SearchArgs => ZQuery[SpotifyService, Throwable, SearchResult])

object Queries {
  val live = Queries(
    args => GetUser.query(args.id),
    args => GetReview.query(args.id),
    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search))
  )
}
