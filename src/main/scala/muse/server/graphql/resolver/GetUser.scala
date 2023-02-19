package muse.server.graphql.resolver

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.User
import muse.server.graphql.subgraph.SpotifyProfile
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.query.{DataSource, Request, ZQuery}
import zio.ZIO

case class GetUser(id: String) extends Request[Nothing, User]

object GetUser {
  def query(maybeId: Option[String]) = maybeId match
    case None     => currentUser
    case Some(id) => ZQuery.succeed(queryByUserId(id))

  def queryByUserId(userId: String) =
    User(userId, GetUserReviews.query(userId), GetSpotifyProfile.query(userId), GetUserPlaylists.boxedQuery(userId))

  def currentUser = for {
    userId <- ZQuery.fromZIO(RequestSession.get[UserSession]).map(_.userId)
  } yield User(userId, GetUserReviews.query(userId, All), GetSpotifyProfile.query(userId), GetUserPlaylists.boxedQuery(userId))

  // TODO: This needs to be revised.
  // Incorporate a limit of how many users can be returned.
  def fromDisplayName(search: String) = ZQuery.fromZIO {
    for {
      spotify        <- RequestSession.get[SpotifyService]
      allUserIds     <- DatabaseService.getUsers.map(_.map(_.userId))
      profiles       <- ZIO.foreachPar(allUserIds)(spotify.getUserProfile)
      lowercaseSearch = search.toLowerCase()
    } yield profiles
      .filter { p =>
        p.displayName.map(_.toLowerCase()).exists(_.contains(lowercaseSearch)) ||
        p.id.toLowerCase().contains(lowercaseSearch)
      }.map { spotifyUser =>
        val userId = spotifyUser.id
        User(
          userId,
          GetUserReviews.query(userId, All),
          ZQuery.succeed(SpotifyProfile.fromSpotify(spotifyUser)),
          GetUserPlaylists.boxedQuery(userId))
      }
  }
}
