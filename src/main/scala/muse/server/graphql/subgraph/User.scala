package muse.server.graphql.subgraph

import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.server.graphql.resolver.{GetSpotifyProfile, GetUser, GetUserPlaylists, GetUserReviews, UserPlaylistsInput}
import muse.server.graphql.{Input, Pagination, subgraph}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

final case class User(
    id: UserId,
    reviews: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    spotifyProfile: ZQuery[RequestSession[SpotifyService], Throwable, SpotifyProfile],
    playlists: UserPlaylistsInput => ZQuery[
      RequestSession[UserSession] & RequestSession[SpotifyService],
      Throwable,
      List[Playlist]]
)

object User {
  def missingSome(userId: UserId, displayName: Option[String], href: String, uri: String, externalUrls: Map[String, String]) =
    User(
      userId,
      GetUserReviews.query(userId),
      GetSpotifyProfile.query(userId),
      GetUserPlaylists.boxedQuery(userId)
    )

}
