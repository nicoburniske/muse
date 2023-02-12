package muse.server.graphql.subgraph

import muse.domain.session.UserSession
import muse.server.graphql.resolver.{GetUser, GetUserReviews, GetUserPlaylists, UserPlaylistsInput}
import muse.server.graphql.{Input, Pagination, subgraph}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

final case class User(
    id: String,
    reviews: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    spotifyProfile: ZQuery[RequestSession[SpotifyService], Throwable, SpotifyProfile],
    playlists: UserPlaylistsInput => ZQuery[
      RequestSession[UserSession] & RequestSession[SpotifyService],
      Throwable,
      List[Playlist]]
)

object User {
  def missingSome(userId: String, displayName: Option[String], href: String, uri: String, externalUrls: Map[String, String]) =
    User(
      userId,
      GetUserReviews.query(userId),
      ZQuery.succeed(SpotifyProfile.missingSome(userId, displayName, href, uri, externalUrls)),
      GetUserPlaylists.boxedQuery(userId)
    )

}
