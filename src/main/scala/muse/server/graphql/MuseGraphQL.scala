package muse.server.graphql

import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import caliban.relay.PageInfo
import caliban.schema.Schema.stringSchema
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.printErrors
import caliban.{CalibanError, GraphQL, GraphQLInterpreter, RootResolver}
import muse.domain.common.EntityType
import muse.domain.common.Types.UserId
import muse.domain.error.*
import muse.domain.event.ReviewUpdate
import muse.domain.mutate.*
import muse.domain.session.UserSession
import muse.domain.spotify.AlbumType
import muse.domain.table.ReviewComment
import muse.domain.{spotify, table}
import muse.server.graphql.resolver.GetSearch.PaginationResult
import muse.server.graphql.resolver.{FeedInput, ReviewConnection, ReviewEdge, UserPlaylistsInput}
import muse.server.graphql.subgraph.*
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyError, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import scala.util.Try

object MuseGraphQL {
  type Env = RequestSession[UserSession] & RequestSession[SpotifyService] & DatabaseService & UserSessions & Hub[ReviewUpdate] &
    Scope

  given Schema[Env, spotify.PlaybackDevice]  = Schema.gen
  given Schema[Env, spotify.ExternalIds]     = Schema.gen
  given Schema[Env, spotify.Copyright]       = Schema.gen
  given Schema[Env, spotify.Restrictions]    = Schema.gen
  given Schema[Env, spotify.AudioAnalysis]   = Schema.gen
  given Schema[Env, spotify.TimeInterval]    = Schema.gen
  given Schema[Env, spotify.AudioSection]    = Schema.gen
  given Schema[Env, spotify.AudioSegment]    = Schema.gen
  given Schema[Env, spotify.AudioFeatures]   = Schema.gen
  given Schema[Env, spotify.PlaybackContext] = Schema.gen

  given Schema[Env, spotify.Modality]               = Schema.gen
  given Schema[Env, spotify.Modality.Major.type]    = Schema.gen
  given Schema[Env, spotify.Modality.Minor.type]    = Schema.gen
  given Schema[Env, spotify.Modality.NoResult.type] = Schema.gen

  given Schema[Env, spotify.ReleaseDatePrecision]            = Schema.gen
  given Schema[Env, spotify.ReleaseDatePrecision.Day.type]   = Schema.gen
  given Schema[Env, spotify.ReleaseDatePrecision.Month.type] = Schema.gen
  given Schema[Env, spotify.ReleaseDatePrecision.Year.type]  = Schema.gen

  given Schema[Env, spotify.AlbumType]                  = Schema.gen
  given Schema[Env, spotify.AlbumType.Album.type]       = Schema.gen
  given Schema[Env, spotify.AlbumType.Compilation.type] = Schema.gen
  given Schema[Env, spotify.AlbumType.Single.type]      = Schema.gen

  given Schema[Env, PlaybackState] = Schema.gen
//  given Schema[Env, SearchResult]               = Schema.gen
//  given Schema[Env, PaginationResult[Album]]    = Schema.gen
//  given Schema[Env, PaginationResult[Artist]]   = Schema.gen
//  given Schema[Env, PaginationResult[Playlist]] = Schema.gen
//  given Schema[Env, PaginationResult[Track]]    = Schema.gen

  given Schema[Env, Review]  = Schema.gen
  given Schema[Env, Comment] = Schema.gen
  given Schema[Env, User]    = Schema.gen

  given Schema[Env, Collaborator]                        = Schema.gen
  given Schema[Env, table.AccessLevel]                   = Schema.gen
  given Schema[Env, table.AccessLevel.Collaborator.type] = Schema.gen
  given Schema[Env, table.AccessLevel.Viewer.type]       = Schema.gen
  given Schema[Any, table.AccessLevel]                   = Schema.gen
  given Schema[Any, table.AccessLevel.Collaborator.type] = Schema.gen
  given Schema[Any, table.AccessLevel.Viewer.type]       = Schema.gen
  given ArgBuilder[table.AccessLevel]                    = ArgBuilder.gen
  given ArgBuilder[table.AccessLevel.Collaborator.type]  = ArgBuilder.gen
  given ArgBuilder[table.AccessLevel.Viewer.type]        = ArgBuilder.gen

  // ReviewEntity Subgraph. Need this for embedded queries in case classes.
  given Schema[Env, ReviewEntity]  = Schema.gen
  given Schema[Env, Album]         = Schema.gen
  given Schema[Env, Artist]        = Schema.gen
  given Schema[Env, Track]         = Schema.gen
  given Schema[Env, Playlist]      = Schema.gen
  given Schema[Env, PlaylistTrack] = Schema.gen

  // ReviewUpdate.
  given Schema[Env, ReviewUpdate]                = Schema.gen
  given Schema[Env, ReviewUpdate.UpdatedComment] = Schema.gen
  given Schema[Env, ReviewUpdate.CreatedComment] = Schema.gen
  given Schema[Env, ReviewUpdate.DeletedComment] = Schema.gen

  // Queries.
  given Schema[Env, UserPlaylistsInput] = Schema.gen
  given ArgBuilder[UserPlaylistsInput]  = ArgBuilder.gen
  given Schema[Env, SpotifyProfile]     = Schema.gen
  given Schema[Env, Pagination]         = Schema.gen
  given ArgBuilder[Pagination]          = ArgBuilder.gen

  given Schema[Env, FeedInput]        = Schema.gen
  given ArgBuilder[FeedInput]         = ArgBuilder.gen
  given Schema[Env, ReviewConnection] = Schema.gen
  given Schema[Env, ReviewEdge]       = Schema.gen
  given Schema[Env, PageInfo]      = Schema.gen
  given ArgBuilder[PageInfo]       = ArgBuilder.gen

  // Mutation.
  given Schema[Env, CreateReview]      = Schema.gen
  given ArgBuilder[CreateReview]       = ArgBuilder.gen
  given Schema[Env, CreateReviewInput] = Schema.gen
  given ArgBuilder[CreateReviewInput]  = ArgBuilder.gen
  given Schema[Env, InitialLink]       = Schema.gen
  given ArgBuilder[InitialLink]        = ArgBuilder.gen

  given Schema[Env, CreateComment]      = Schema.gen
  given ArgBuilder[CreateComment]       = ArgBuilder.gen
  given Schema[Env, CreateCommentInput] = Schema.gen
  given ArgBuilder[CreateCommentInput]  = ArgBuilder.gen

  given Schema[Env, ShareReview]      = Schema.gen
  given ArgBuilder[ShareReview]       = ArgBuilder.gen
  given Schema[Env, ShareReviewInput] = Schema.gen
  given ArgBuilder[ShareReviewInput]  = ArgBuilder.gen

  given Schema[Env, UpdateReview]      = Schema.gen
  given ArgBuilder[UpdateReview]       = ArgBuilder.gen
  given Schema[Env, UpdateReviewInput] = Schema.gen
  given ArgBuilder[UpdateReviewInput]  = ArgBuilder.gen

  given Schema[Env, UpdateReviewEntity]      = Schema.gen
  given ArgBuilder[UpdateReviewEntity]       = ArgBuilder.gen
  given Schema[Env, UpdateReviewEntityInput] = Schema.gen
  given ArgBuilder[UpdateReviewEntityInput]  = ArgBuilder.gen

  given Schema[Env, UpdateReviewLink]      = Schema.gen
  given ArgBuilder[UpdateReviewLink]       = ArgBuilder.gen
  given Schema[Env, UpdateReviewLinkInput] = Schema.gen
  given ArgBuilder[UpdateReviewLinkInput]  = ArgBuilder.gen

  given Schema[Env, UpdateComment]      = Schema.gen
  given ArgBuilder[UpdateComment]       = ArgBuilder.gen
  given Schema[Env, UpdateCommentInput] = Schema.gen
  given ArgBuilder[UpdateCommentInput]  = ArgBuilder.gen

  given Schema[Env, UpdateCommentIndex]      = Schema.gen
  given ArgBuilder[UpdateCommentIndex]       = ArgBuilder.gen
  given Schema[Env, UpdateCommentIndexInput] = Schema.gen
  given ArgBuilder[UpdateCommentIndexInput]  = ArgBuilder.gen

  given Schema[Env, LinkReviews]      = Schema.gen
  given ArgBuilder[LinkReviews]       = ArgBuilder.gen
  given Schema[Env, LinkReviewsInput] = Schema.gen
  given ArgBuilder[LinkReviewsInput]  = ArgBuilder.gen

  given Schema[Env, DeleteReview]      = Schema.gen
  given ArgBuilder[DeleteReview]       = ArgBuilder.gen
  given Schema[Env, DeleteReviewInput] = Schema.gen
  given ArgBuilder[DeleteReviewInput]  = ArgBuilder.gen

  given Schema[Env, DeleteComment]      = Schema.gen
  given ArgBuilder[DeleteComment]       = ArgBuilder.gen
  given Schema[Env, DeleteCommentInput] = Schema.gen
  given ArgBuilder[DeleteCommentInput]  = ArgBuilder.gen

  given Schema[Env, DeleteReviewLink]      = Schema.gen
  given ArgBuilder[DeleteReviewLink]       = ArgBuilder.gen
  given Schema[Env, DeleteReviewLinkInput] = Schema.gen
  given ArgBuilder[DeleteReviewLinkInput]  = ArgBuilder.gen

  // Input Wrappers
  given Schema[Env, UserId] = stringSchema.contramap(UserId(_))
  given ArgBuilder[UserId]  = ArgBuilder.string.map(UserId(_))

  given Schema[Env, UserInput]          = Schema.gen
  given ArgBuilder[UserInput]           = ArgBuilder.gen
  given Schema[Env, SearchUserInput]    = Schema.gen
  given ArgBuilder[SearchUserInput]     = ArgBuilder.gen
  given Schema[Env, ReviewInput]        = Schema.gen
  given ArgBuilder[ReviewInput]         = ArgBuilder.gen
  given Schema[Env, ReviewsInput]       = Schema.gen
  given ArgBuilder[ReviewsInput]        = ArgBuilder.gen
  given Schema[Env, SpotifyEntityInput] = Schema.gen
  given ArgBuilder[SpotifyEntityInput]  = ArgBuilder.gen

  given Schema[Env, ReviewEntityInput] = Schema.gen
  given ArgBuilder[ReviewEntityInput]  = ArgBuilder.gen

  // Entity Type Enum.
  given Schema[Env, EntityType]               = Schema.gen
  given ArgBuilder[EntityType]                = ArgBuilder.gen
  given Schema[Any, EntityType.Album.type]    = Schema.gen
  given ArgBuilder[EntityType.Album.type]     = ArgBuilder.gen
  given Schema[Any, EntityType.Artist.type]   = Schema.gen
  given ArgBuilder[EntityType.Artist.type]    = ArgBuilder.gen
  given Schema[Any, EntityType.Track.type]    = Schema.gen
  given ArgBuilder[EntityType.Track.type]     = ArgBuilder.gen
  given Schema[Any, EntityType.Playlist.type] = Schema.gen
  given ArgBuilder[EntityType.Playlist.type]  = ArgBuilder.gen

  given Schema[Env, CommentInput]  = Schema.gen
  given ArgBuilder[CommentInput]   = ArgBuilder.gen
  given Schema[Env, CommentsInput] = Schema.gen
  given ArgBuilder[CommentsInput]  = ArgBuilder.gen

  given Schema[Env, Queries]       = Schema.gen
  given Schema[Env, Mutations]     = Schema.gen
  given Schema[Env, Subscriptions] = Schema.gen

  // Subscription Input.
  given Schema[Env, NowPlayingInput]    = Schema.gen
  given ArgBuilder[NowPlayingInput]     = ArgBuilder.gen
  given Schema[Env, ReviewUpdatesInput] = Schema.gen
  given ArgBuilder[ReviewUpdatesInput]  = ArgBuilder.gen

  // TODO: give this another shot?
//  given errorSchema[A](using Schema[Any, A]): Schema[Any, IO[Throwable | MuseError, A]] =
//    Schema.customErrorEffectSchema((e: Throwable | MuseError) =>
//      e.match
//        case museError: MuseError => ExecutionError(museError.message, innerThrowable = Some(MuseThrowable(museError)))
//        case throwable: Throwable => ExecutionError(throwable.getMessage, innerThrowable = Some(throwable))
//    )

  val api = caliban.graphQL[Env, Queries, Mutations, Subscriptions](
    RootResolver(Queries.live, Mutations.live, Subscriptions.live)) @@ printErrors @@ apolloTracing

  val interpreter = api.interpreter.map(errorHandler(_))

  // TODO: Consider handling Spotify 404 error.
  private def errorHandler[R](
      interpreter: GraphQLInterpreter[R, CalibanError]
  ): GraphQLInterpreter[R, CalibanError] = interpreter.mapError {
    case err @ ExecutionError(_, _, _, Some(m: MuseError), _)    =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue(m.code),
            "message"   -> StringValue(m.message)
          ))))
    case err @ ExecutionError(_, _, _, Some(e: SpotifyError), _) =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("SERVER_ERROR_SPOTIFY"),
            "message"   -> StringValue(e.getMessage)
          ))))
    case err @ ExecutionError(_, _, _, Some(e: Throwable), _)    =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("SERVER_ERROR"),
            "errorType" -> StringValue(e.getClass.getSimpleName),
            "message"   -> StringValue(e.getMessage)
          ))))
    case err: ExecutionError                                     =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("EXECUTION_ERROR")))))
    case err: ValidationError                                    =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("VALIDATION_ERROR")))))
    case err: ParsingError                                       =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("PARSING_ERROR")))))
  }
}
