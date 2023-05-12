package muse.server.graphql.resolver

import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.{SpotifyProfile, User}
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import muse.server.graphql.Helpers.*
import zio.{Reloadable, ZIO}
import zio.query.{DataSource, Request, ZQuery}

case class GetUser(id: UserId) extends Request[Nothing, User]

object GetUser {
  type Env = Reloadable[UserSession] & DatabaseService

  def query(maybeId: Option[UserId]) = maybeId match
    case None     => currentUser
    case Some(id) => ZQuery.succeed(queryByUserId(id))
    
  def queryByUserId(userId: UserId) = User.fromId(userId)
  def currentUser = ZQuery.fromZIO(getUserId).map(userId => User.fromId(userId))

  // TODO: This needs to be revised.
  // Incorporate a limit of how many users can be returned.

  type SearchEnv = Reloadable[SpotifyService] with DatabaseService

  def fromDisplayName(search: String) = ZQuery.fromZIO {
    for {
      spotify        <- getSpotify
      allUserIds     <- DatabaseService.getUsers.map(_.map(_.userId))
      profiles       <- ZIO.foreachPar(allUserIds)(spotify.getUserProfile)
      lowercaseSearch = search.toLowerCase()
    } yield profiles
      .filter { p =>
        p.displayName.map(_.toLowerCase()).exists(_.contains(lowercaseSearch)) ||
        p.id.toLowerCase().contains(lowercaseSearch)
      }.map { spotifyUser =>
        val userId = UserId(spotifyUser.id)
        User(
          userId,
          GetUserReviews.query(userId, All),
          ZQuery.succeed(SpotifyProfile.fromSpotify(spotifyUser)),
          GetUserPlaylists.boxedQuery(userId))
      }
  }
}
