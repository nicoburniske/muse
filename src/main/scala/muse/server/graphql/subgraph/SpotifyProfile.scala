package muse.server.graphql.subgraph

import muse.domain.spotify
import muse.server.graphql.Resolvers.spotifyProfile
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class SpotifyProfile(
    id: String,
    displayName: Option[String],
    href: String,
    uri: String,
    externalUrls: Map[String, String],
    images: ZQuery[SpotifyService, Throwable, List[String]],
    numFollowers: ZQuery[SpotifyService, Throwable, Int])

object SpotifyProfile {
  def fromSpotify(u: spotify.User): SpotifyProfile = {
    val images    = u.images.fold(spotifyProfile(u.id).flatMap(_.images))(i => ZQuery.succeed(i.map(_.url)))
    val followers =
      u.followers.fold(spotifyProfile(u.id).flatMap(_.numFollowers))(f => ZQuery.succeed(f.total))
    SpotifyProfile(
      u.id,
      u.displayName,
      u.href,
      u.uri,
      u.externalUrls,
      images,
      followers
    )
  }

  def missingSome(
      id: String,
      displayName: Option[String],
      href: String,
      uri: String,
      externalUrls: Map[String, String]): SpotifyProfile =
    val profile = spotifyProfile(id)
    SpotifyProfile.apply(
      id,
      displayName,
      href,
      uri,
      externalUrls,
      profile.flatMap(_.images),
      profile.flatMap(_.numFollowers)
    )
}
