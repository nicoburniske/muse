package muse.server.graphql.subgraph

import caliban.schema.Schema
import muse.domain.spotify
import muse.server.graphql.resolver.GetSpotifyProfile
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class SpotifyProfile(
    id: String,
    displayName: Option[String],
    href: String,
    uri: String,
    externalUrls: Map[String, String],
    images: List[String],
    numFollowers: Option[Int])

object SpotifyProfile {
  def fromSpotify(u: spotify.PublicUser): SpotifyProfile = {
    SpotifyProfile(
      u.id,
      u.displayName,
      u.href,
      u.uri,
      u.externalUrls,
      u.images.map(_.url),
      u.followers.map(_.total)
    )
  }
}
