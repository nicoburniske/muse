package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import caliban.schema.Schema.stringSchema
import muse.domain.common.EntityType
import muse.domain.common.Types.UserId
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.domain.spotify.PlaybackDevice
import muse.server.graphql.Pagination.Default
import muse.server.graphql.resolver.{GetAlbum, GetComment, GetPlaylist, GetReview, GetSearch, GetTrack, GetUser, GetUserPlaylists}
import muse.server.graphql.subgraph.{Album, Comment, Playlist, Review, SearchResult, Track, User}
import muse.service.RequestSession
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

final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])

// TODO: Integrate "Input" for arguments.
final case class Queries(
    user: UserInput => ZQuery[GetUser.Env, Nothing, User],
    userMaybe: UserInput => ZQuery[GetUser.Env, Throwable, User],
    searchUser: SearchUserInput => ZQuery[GetUser.SearchEnv, Throwable, List[User]],
    review: ReviewInput => ZQuery[GetReview.Env, Throwable, Option[Review]],
    reviews: ReviewsInput => ZQuery[GetReview.Env, Throwable, List[Review]],
    comment: CommentInput => ZQuery[GetComment.Env, Throwable, Option[Comment]],
    comments: CommentsInput => ZQuery[GetComment.Env, Throwable, List[Comment]],
    //    search: SearchArgs => ZQuery[RequestSession[SpotifyService], Throwable, SearchResult],
    getPlaylist: SpotifyEntityInput => ZQuery[RequestSession[SpotifyService], Throwable, Playlist],
    getAlbum: SpotifyEntityInput => ZQuery[RequestSession[SpotifyService], Throwable, Album],
    getTrack: SpotifyEntityInput => ZQuery[RequestSession[SpotifyService], Throwable, Track]
)

object Queries {
  val live = Queries(
    args => GetUser.query(args.id).orDie,
    args => GetUser.query(args.id),
    args => GetUser.fromDisplayName(args.displayName),
    args => GetReview.query(args.id),
    args => GetReview.multiQuery(args.reviewIds),
    args => GetComment.query(args.id),
    args => ZQuery.foreachPar(args.commentIds)(id => GetComment.query(id)).map(_.flatten),
//    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search)),
    args => GetPlaylist.query(args.id),
    args => GetAlbum.query(args.id),
    args => GetTrack.query(args.id)
  )
}
