package muse.server.graphql

import muse.domain.common.EntityType
import muse.server.graphql.subgraph.{Review, SearchResult, User}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.util.UUID

final case class UserArgs(id: String)

final case class ReviewsArgs(id: UUID)

final case class SearchArgs(query: String, entityTypes: Set[EntityType])

final case class Queries(
    user: UserArgs => ZQuery[DatabaseQueries, Throwable, User],
    reviews: ReviewsArgs => ZQuery[DatabaseQueries, Throwable, Option[Review]],
    search: SearchArgs => ZQuery[SpotifyService, Throwable, SearchResult])

object Queries {
  val live = Queries(
    args => Resolvers.getUser(args.id),
    args => Resolvers.getReview(args.id),
    args => Resolvers.search(args.query, args.entityTypes))
}
