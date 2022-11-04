package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import muse.domain.common.EntityType
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.PlaybackDevice
import muse.server.graphql.Pagination.Default
import muse.server.graphql.resolver.{GetReview, GetSearch, GetUser, GetPlaylist}
import muse.server.graphql.subgraph.{Review, SearchResult, User, Playlist}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

import java.util.UUID

final case class UserArgs(id: Option[String])

final case class ReviewsArgs(id: UUID)

final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])

final case class PlaylistInput(id: String)
// TODO: Integrate "Input" for arguments.
final case class Queries(
    user: UserArgs => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, User],
    review: ReviewsArgs => ZQuery[DatabaseService, Throwable, Option[Review]],
    search: SearchArgs => ZQuery[RequestSession[SpotifyService], Throwable, SearchResult],
    availableDevices: ZIO[RequestSession[SpotifyService], Throwable, List[PlaybackDevice]],
    getPlaylist: PlaylistInput => ZQuery[RequestSession[SpotifyService], Throwable, Playlist])

object Queries {
  val live = Queries(
    args => GetUser.query(args.id),
    args => GetReview.query(args.id),
    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search)),
    RequestSession.get[SpotifyService].flatMap(_.getAvailableDevices.map(_.toList)),
    args => GetPlaylist.query(args.id)
  )
}
