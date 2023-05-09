package muse.server.graphql.resolver

import muse.domain.common.Types.UserId
import muse.server.graphql.Helpers.getSpotify
import muse.server.graphql.subgraph.SpotifyProfile
import muse.service.spotify.SpotifyService
import zio.{Reloadable, ZIO}
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetSpotifyProfile(id: UserId) extends Request[Throwable, SpotifyProfile]

object GetSpotifyProfile {
  type Env = Reloadable[SpotifyService]
  
  def query(userId: UserId): ZQuery[Env, Throwable, SpotifyProfile] =
    ZQuery.fromRequest(GetSpotifyProfile(userId))(spotifyProfileDataSource)

  val spotifyProfileDataSource: DataSource[Env, GetSpotifyProfile] =
    DataSource.fromFunctionZIO("SpotifyProfileDataSource") { req =>
      getSpotify
        .flatMap(_.getUserProfile(req.id))
        .map(SpotifyProfile.fromSpotify)
    }
}
