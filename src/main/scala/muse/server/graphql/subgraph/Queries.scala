package muse.server.graphql.subgraph

import muse.service.persist.DatabaseQueries
import zio.query.ZQuery

import java.util.UUID

final case class UserArgs(id: String)

final case class ReviewsArgs(id: UUID)

final case class Queries(
    user: UserArgs => ZQuery[DatabaseQueries, Throwable, User],
    reviews: ReviewsArgs => ZQuery[DatabaseQueries, Throwable, Option[Review]])
