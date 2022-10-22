package muse.server.graphql.subgraph

import muse.domain.session.UserSession
import muse.server.graphql.resolver.GetUser
import muse.server.graphql.subgraph
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class User(
    id: String,
    reviews: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    spotifyProfile: ZQuery[RequestSession[SpotifyService], Throwable, SpotifyProfile]
)

object User {
  def missingSome(userId: String, displayName: Option[String], href: String, uri: String, externalUrls: Map[String, String]) =
    User(
      userId,
      GetUser.queryByUserId(userId).flatMap(_.reviews),
      ZQuery.succeed(SpotifyProfile.missingSome(userId, displayName, href, uri, externalUrls)))
}
