package muse.server.graphql.resolver

import muse.server.graphql.subgraph.SpotifyProfile
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetSpotifyProfile(id: String) extends Request[Throwable, SpotifyProfile]

object GetSpotifyProfile {
  def query(userId: String) =
    ZQuery.fromRequest(GetSpotifyProfile(userId))(spotifyProfileDataSource)

  val spotifyProfileDataSource: DataSource[SpotifyService, GetSpotifyProfile] =
    DataSource.fromFunctionZIO("SpotifyProfileDataSource") { req =>
      SpotifyService
        .getUserProfile(req.id)
        .map(SpotifyProfile.fromSpotify)
    }
}
