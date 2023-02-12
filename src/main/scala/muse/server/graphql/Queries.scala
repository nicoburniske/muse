package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import muse.domain.common.EntityType
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.PlaybackDevice
import muse.server.graphql.Pagination.Default
import muse.server.graphql.resolver.{GetAlbum, GetPlaylist, GetReview, GetSearch, GetTrack, GetUser, GetUserPlaylists}
import muse.server.graphql.subgraph.{Album, Playlist, Review, SearchResult, Track, User}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

import java.util.UUID

final case class UserArgs(id: Option[String])

final case class ReviewArgs(id: UUID)

final case class ReviewsArgs(reviewIds: List[UUID])

final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])

final case class EntityId(id: String)
// TODO: Integrate "Input" for arguments.
final case class Queries(
    user: UserArgs => ZQuery[RequestSession[UserSession] & DatabaseService, Nothing, User],
    userMaybe: UserArgs => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, User],
    review: ReviewArgs => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, Option[Review]],
    reviews: ReviewsArgs => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    search: SearchArgs => ZQuery[RequestSession[SpotifyService], Throwable, SearchResult],
    getPlaylist: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Playlist],
    getAlbum: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Album],
    getTrack: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Track]
)

object Queries {
  val live = Queries(
    args => GetUser.query(args.id).orDie,
    args => GetUser.query(args.id),
    args => GetReview.query(args.id),
    args => GetReview.multiQuery(args.reviewIds),
    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search)),
    args => GetPlaylist.query(args.id),
    args => GetAlbum.query(args.id),
    args => GetTrack.query(args.id)
  )
}
