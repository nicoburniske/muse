package muse.server.graphql.subgraph

import muse.server.graphql.Resolvers.getUserReviews
import muse.server.graphql.subgraph
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class User(
    id: String,
    reviews: ZQuery[DatabaseOps, Throwable, List[Review]],
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
      getUserReviews(userId),
      ZQuery.succeed(SpotifyProfile.missingSome(userId, displayName, href, uri, externalUrls)))
}
