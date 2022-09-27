package muse.server.graphql.subgraph

import muse.server.graphql.resolver.GetUserReviews
import muse.server.graphql.subgraph
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class User(
    id: String,
    reviews: ZQuery[DatabaseService, Throwable, List[Review]],
    spotifyProfile: ZQuery[SpotifyService, Throwable, SpotifyProfile]
)

object User {
  def missingSome(
      userId: String,
      displayName: Option[String],
      href: String,
      uri: String,
      externalUrls: Map[String, String]) =
    User(
      userId,
      GetUserReviews.query(userId),
      ZQuery.succeed(SpotifyProfile.missingSome(userId, displayName, href, uri, externalUrls)))
}
