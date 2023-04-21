package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import caliban.schema.Schema.stringSchema
import caliban.schema.{ArgBuilder, Schema}
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
final case class SearchUserInput(displayName: String) derives Schema.SemiAuto, ArgBuilder
final case class ReviewInput(id: UUID) derives Schema.SemiAuto, ArgBuilder
final case class ReviewsInput(reviewIds: List[UUID]) derives Schema.SemiAuto, ArgBuilder
final case class CommentInput(id: Long) 
final case class CommentsInput(commentIds: List[Long]) 

final case class SearchArgs(
    query: String,
    types: Set[EntityType],
    @GQLDefault(Default.Search.annotation) pagination: Option[Pagination])
    derives Schema.SemiAuto,
      ArgBuilder

final case class EntityId(id: String) derives Schema.SemiAuto, ArgBuilder
// TODO: Integrate "Input" for arguments.
final case class Queries(
    user: UserInput => ZQuery[RequestSession[UserSession] & DatabaseService, Nothing, User],
    userMaybe: UserInput => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, User],
    searchUser: SearchUserInput => ZQuery[
      RequestSession[UserSession] & RequestSession[SpotifyService] & DatabaseService,
      Throwable,
      List[User]],
    review: ReviewInput => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, Option[Review]],
    reviews: ReviewsInput => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Review]],
    comment: CommentInput => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, Option[Comment]],
//    comments: CommentsInput => ZQuery[RequestSession[UserSession] & DatabaseService, Throwable, List[Comment]],
    //    search: SearchArgs => ZQuery[RequestSession[SpotifyService], Throwable, SearchResult],
    getPlaylist: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Playlist],
    getAlbum: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Album],
    getTrack: EntityId => ZQuery[RequestSession[SpotifyService], Throwable, Track]
)

object Queries {
  val live = Queries(
    args => GetUser.query(args.id).orDie,
    args => GetUser.query(args.id),
    args => GetUser.fromDisplayName(args.displayName),
    args => GetReview.query(args.id),
    args => GetReview.multiQuery(args.reviewIds),
    args => GetComment.query(args.id),
//    args => 
//    args => GetSearch.query(args.query, args.types, args.pagination.getOrElse(Default.Search)),
    args => GetPlaylist.query(args.id),
    args => GetAlbum.query(args.id),
    args => GetTrack.query(args.id)
  )
}
