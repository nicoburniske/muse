package muse.server.graphql.subgraph

import muse.domain.session.UserSession
import muse.server.graphql.resolver.{GetUser, GetUserReviews}
import muse.server.graphql.{Input, Pagination, subgraph}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

type PlaylistRequest = ZIO[RequestSession[UserSession] & RequestSession[SpotifyService], Throwable, List[Playlist]]
final case class User(
    id: String,
    reviews: ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    spotifyProfile: ZQuery[RequestSession[SpotifyService], Throwable, SpotifyProfile],
    playlists: Input[SearchUserPlaylists] => PlaylistRequest = User.fromInput
)

case class SearchUserPlaylists(pagination: Pagination, search: Option[String])

object User {
  def missingSome(userId: String, displayName: Option[String], href: String, uri: String, externalUrls: Map[String, String]) =
    User(
      userId,
      GetUserReviews.query(userId),
      ZQuery.succeed(SpotifyProfile.missingSome(userId, displayName, href, uri, externalUrls)),
      fromInput
    )

  private def fromInput(i: Input[SearchUserPlaylists]) = User.playlists(i.input.pagination, i.input.search)

  private def playlists(p: Pagination, search: Option[String]): PlaylistRequest = for {
    userId    <- RequestSession.get[UserSession].map(_.userId)
    spotify   <- RequestSession.get[SpotifyService]
    playlists <- searchThroughPlaylists(spotify, userId, search, p.first, p.offset, Nil)
  } yield playlists

  val MAX_PLAYLIST_REQUEST = 50

  def searchThroughPlaylists(
      spotifyService: SpotifyService,
      userId: String,
      query: Option[String],
      total: Int,
      currentPosition: Int,
      playlists: List[Playlist]): ZIO[Any, Throwable, List[Playlist]] =
    if (playlists.size >= total) {
      ZIO.succeed(playlists)
    } else {
      for {
        newPlaylists <- spotifyService.getUserPlaylists(userId, MAX_PLAYLIST_REQUEST, Some(currentPosition))
        // Only want the ones containing the search in their name.
        allPlaylists  = playlists ++ newPlaylists.items.filter(p => query.exists(p.name.contains(_))).map(Playlist.fromSpotify)
        result       <- newPlaylists.next.fold(ZIO.succeed(allPlaylists)) { _ =>
                          searchThroughPlaylists(
                            spotifyService,
                            userId,
                            query,
                            total,
                            currentPosition + MAX_PLAYLIST_REQUEST,
                            allPlaylists)
                        }
      } yield result
    }
}
