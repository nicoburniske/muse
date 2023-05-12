package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import caliban.schema.Schema.stringSchema
import muse.domain.common.EntityType
import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.domain.spotify.PlaybackDevice
import muse.server.graphql.Pagination.Default
import muse.server.graphql.resolver.*
import muse.server.graphql.subgraph.*
import muse.service.persist.DatabaseService
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

import java.util.UUID

final case class UserInput(id: Option[UserId])
final case class SearchUserInput(displayName: String)
final case class ReviewInput(id: UUID)
final case class ReviewsInput(reviewIds: List[UUID])
final case class CommentInput(id: Long)
final case class CommentsInput(commentIds: List[Long])
final case class SpotifyEntityInput(id: String)

// TODO: add search with Relay Pagination.
final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])

final case class Queries(
    me: ZQuery[GetUser.Env, Nothing, PrivateUser],
    user: UserInput => ZQuery[GetUser.Env, Nothing, User],
    searchUser: SearchUserInput => ZQuery[GetUser.SearchEnv, Throwable, List[User]],
    review: ReviewInput => ZQuery[GetReview.Env, Throwable, Option[Review]],
    reviews: ReviewsInput => ZQuery[GetReview.Env, Throwable, List[Review]],
    feed: FeedInput => ZQuery[GetFeed.Env, Throwable, ReviewConnection],
    comment: CommentInput => ZQuery[GetComment.Env, Throwable, Option[Comment]],
    comments: CommentsInput => ZQuery[GetComment.Env, Throwable, List[Comment]],
    //    search: SearchArgs => ZQuery[SpotifyService, Throwable, SearchResult],
    getPlaylist: SpotifyEntityInput => ZQuery[GetPlaylist.Env, Throwable, Playlist],
    getAlbum: SpotifyEntityInput => ZQuery[GetAlbum.Env, Throwable, Album],
    getTrack: SpotifyEntityInput => ZQuery[GetTrack.Env, Throwable, Track]
)

object Queries {
  val live = Queries(
    PrivateUser.query,
    args => GetUser.query(args.id),
    args => GetUser.fromDisplayName(args.displayName),
    args => GetReview.query(args.id),
    args => GetReview.multiQuery(args.reviewIds),
    args => GetFeed.query(args),
    args => GetComment.query(args.id),
    args => ZQuery.foreachPar(args.commentIds)(id => GetComment.query(id)).map(_.flatten),
//    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search)),
    args => GetPlaylist.query(args.id),
    args => GetAlbum.query(args.id),
    args => GetTrack.query(args.id)
  )
}
