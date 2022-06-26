package muse.server.graphql.resolver

import muse.server.graphql.subgraph.User
import muse.service.persist.DatabaseOps
import zio.query.{DataSource, Request, ZQuery}

case class GetUser(id: String) extends Request[Nothing, User]

object GetUser {
  def query(id: String) = ZQuery.fromRequest(GetUser(id))(UserDataSource)

  val UserDataSource: DataSource[DatabaseOps, GetUser] =
    DataSource.fromFunction("UserDataSource") { req =>
      User(req.id, GetUserReviews.query(req.id), GetSpotifyProfile.query(req.id))
    }
}
