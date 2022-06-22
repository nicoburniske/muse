package muse.server.graphql.subgraph

import muse.service.persist.DatabaseQueries
import zio.query.ZQuery

final case class User(
    id: String,
    //    reviews: Pagination => ZQuery[DatabaseQueries, Nothing, List[Review]]
    reviews: ZQuery[DatabaseQueries, Throwable, List[Review]]
    // TODO: incorporate all spotify stuff.
)
