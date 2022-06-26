package muse.server.graphql.resolver

import muse.server.graphql.subgraph.SpotifyProfile
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

object GetSpotifyProfile {
  def query(userId: String): ZQuery[SpotifyService, Throwable, SpotifyProfile] = ZQuery.fromZIO(
    SpotifyService.getUserProfile(userId).map(SpotifyProfile.fromSpotify)
  )
}
