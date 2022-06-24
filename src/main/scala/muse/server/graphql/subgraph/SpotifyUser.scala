package muse.server.graphql.subgraph

import muse.domain.spotify
import muse.server.graphql.Resolvers.spotifyProfile
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class SpotifyUser(
    id: String,
    href: String,
    uri: String,
    externalUrls: Map[String, String],
    images: ZQuery[SpotifyService, Throwable, List[String]],
    numFollowers: ZQuery[SpotifyService, Throwable, Int])

object SpotifyUser {
  def fromSpotify(u: spotify.User): SpotifyUser =
    SpotifyUser(
      u.id,
      u.href,
      u.uri,
      u.externalUrls,
      ZQuery.succeed(u.images),
      ZQuery.succeed(u.followers.total)
    )

  def missingSome(id: String, href: String, uri: String, externalUrls: Map[String, String]): SpotifyUser =
    val profile = spotifyProfile(id)
    SpotifyUser.apply(
      id,
      href,
      uri,
      externalUrls,
      profile.flatMap(_.images),
      profile.flatMap(_.numFollowers)
    )
}
