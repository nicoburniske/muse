package muse.server.graphql.resolver

import muse.server.graphql.subgraph.SpotifyProfile
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetSpotifyProfile(id: String) extends Request[Throwable, SpotifyProfile]

object GetSpotifyProfile {
  def query(userId: String) =
    ZQuery.fromRequest(GetSpotifyProfile(userId))(spotifyProfileDataSource)

  val spotifyProfileDataSource: DataSource[RequestSession[SpotifyService], GetSpotifyProfile] =
    DataSource.fromFunctionZIO("SpotifyProfileDataSource") { req =>
      RequestSession
        .get[SpotifyService]
        .flatMap(_.getUserProfile(req.id))
        .map(SpotifyProfile.fromSpotify)
    }
}
