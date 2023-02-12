package muse.server.graphql.resolver

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.User
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import zio.query.{DataSource, Request, ZQuery}

case class GetUser(id: String) extends Request[Nothing, User]

object GetUser {
  def query(maybeId: Option[String]) = maybeId match
    case None     => currentUser
    case Some(id) => ZQuery.succeed(queryByUserId(id))

  def queryByUserId(userId: String) = User(userId, GetUserReviews.query(userId), GetSpotifyProfile.query(userId), GetUserPlaylists.boxedQuery(userId))

  def currentUser = for {
    userId <- ZQuery.fromZIO(RequestSession.get[UserSession]).map(_.userId)
  } yield User(userId, GetUserReviews.query(userId, All), GetSpotifyProfile.query(userId), GetUserPlaylists.boxedQuery(userId))
}
