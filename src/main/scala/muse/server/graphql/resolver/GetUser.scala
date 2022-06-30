package muse.server.graphql.resolver

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.User
import muse.service.persist.DatabaseOps
import zio.query.{DataSource, Request, ZQuery}

case class GetUser(id: String) extends Request[Nothing, User]

object GetUser {

  def query(maybeId: Option[String]) = maybeId match
    case None     => currentUser
    case Some(id) => queryByUserId(id)

  def queryByUserId(userId: String) =
    ZQuery.succeed(User(userId, GetUserReviews.query(userId), GetSpotifyProfile.query(userId)))

  def currentUser: ZQuery[Auth[UserSession] & DatabaseOps, Unauthorized, User] = for {
    userSession <- ZQuery.fromZIO(Auth.currentUser[UserSession])
    user        <- queryByUserId(userSession.id)
  } yield user
}
