package muse.server.graphql.subgraph

import muse.domain.common.Types.UserId
import muse.server.graphql.resolver.*
import muse.server.graphql.Helpers.*
import muse.server.graphql.{Input, Pagination, subgraph}
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService

import zio.ZIO
import zio.query.ZQuery

final case class User(
    id: UserId,
    reviews: ZQuery[GetUserReviews.Env, Throwable, List[Review]],
    spotifyProfile: ZQuery[GetSpotifyProfile.Env, Throwable, SpotifyProfile],
    playlists: UserPlaylistsInput => ZQuery[GetUserPlaylists.Env, Throwable, List[Playlist]]
)

object User {
  def fromId(userId: UserId) =
    User.apply(userId, GetUserReviews.query(userId), GetSpotifyProfile.query(userId), GetUserPlaylists.boxedQuery(userId))
}

final case class PrivateUser(
    id: UserId,
    reviews: ZQuery[GetUserReviews.Env, Throwable, List[Review]],
    spotifyProfile: ZQuery[GetSpotifyProfile.Env, Throwable, PrivateSpotifyProfile],
    playlists: UserPlaylistsInput => ZQuery[GetUserPlaylists.Env, Throwable, List[Playlist]]
)

object PrivateUser {
  def query = ZQuery.fromZIO(getSession.map(session => PrivateUser.fromId(session.userId)))

  def fromId(userId: UserId) = {
    val profile = ZQuery.fromZIO(getSpotify.flatMap(_.getCurrentUserProfile).map(PrivateSpotifyProfile.fromSpotify))
    PrivateUser(userId, GetUserReviews.query(userId), profile, GetUserPlaylists.boxedQuery(userId))
  }
}
