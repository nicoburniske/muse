package muse.server.graphql.subgraph

import muse.domain.spotify
import muse.server.graphql.resolver.GetSpotifyProfile
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class SpotifyProfile(
    id: String,
    displayName: ZQuery[RequestSession[SpotifyService], Throwable, Option[String]],
    href: String,
    uri: String,
    externalUrls: Map[String, String],
    images: ZQuery[RequestSession[SpotifyService], Throwable, List[String]],
    numFollowers: ZQuery[RequestSession[SpotifyService], Throwable, Int])

object SpotifyProfile {
  def fromSpotify(u: spotify.User): SpotifyProfile = {
    // Followers and images are not included by spotify api when looking into playlist metadata.
    val images    =
      u.images.fold(GetSpotifyProfile.query(u.id).flatMap(_.images))(i => ZQuery.succeed(i.map(_.url)))
    val followers =
      u.followers.fold(GetSpotifyProfile.query(u.id).flatMap(_.numFollowers))(f => ZQuery.succeed(f.total))
    SpotifyProfile(
      u.id,
      u.displayName.fold(ZQuery.succeed(None))(name => ZQuery.succeed(Some(name))),
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
    val profile     = GetSpotifyProfile.query(id)
    val nameOrFetch = displayName.fold(GetSpotifyProfile.query(id).flatMap(_.displayName))(n => ZQuery.succeed(Some(n)))
    SpotifyProfile.apply(
      id,
      nameOrFetch,
      href,
      uri,
      externalUrls,
      profile.flatMap(_.images),
      profile.flatMap(_.numFollowers)
    )
}
