package muse.server.graphql

import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import caliban.schema.{GenericSchema, Schema}
import caliban.wrappers.Wrappers.printErrors
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.{CalibanError, GraphQL, GraphQLInterpreter, RootResolver}
import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, InvalidEntity, Unauthorized}
import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.domain.spotify.AlbumType
import muse.domain.table.ReviewComment
import muse.server.MuseMiddleware.Auth
import muse.server.graphql.subgraph.{
  Album,
  Artist,
  Comment,
  Playlist,
  Review,
  ReviewEntity,
  SearchResult,
  Track,
  User
}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.Console.printLine
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import scala.util.Try

object MuseGraphQL {
  given userSchema: Schema[DatabaseOps & SpotifyService, User] = Schema.gen

  given reviewSchema: Schema[DatabaseOps & SpotifyService, Review] = Schema.gen

  given commentsSchema: Schema[DatabaseOps & SpotifyService, Comment] = Schema.gen

  given entitySchema: Schema[SpotifyService, ReviewEntity] = Schema.gen

  given albumSchema: Schema[SpotifyService, Album] = Schema.gen

  given artistSchema: Schema[SpotifyService, Artist] = Schema.gen

  given playlistSchema: Schema[SpotifyService, Playlist] = Schema.gen

  given trackSchema: Schema[SpotifyService, Track] = Schema.gen

  given userArgs: Schema[DatabaseOps, UserArgs] = Schema.gen

  given createReview: Schema[Auth[UserSession] & DatabaseOps, CreateReview] = Schema.gen

  given createComment: Schema[Auth[UserSession] & DatabaseOps, CreateComment] = Schema.gen

  given updateReview: Schema[Auth[UserSession] & DatabaseOps, UpdateReview] = Schema.gen

  given updateComment: Schema[Auth[UserSession] & DatabaseOps, UpdateComment] = Schema.gen

  given searchSchema: Schema[SpotifyService, SearchResult] = Schema.gen

  type Env = Auth[UserSession] & DatabaseOps & SpotifyService

  val api =
    GraphQL.graphQL[Env, Queries, Mutations, Unit](
      RootResolver(Queries.live, Mutations.live)) @@ printErrors @@ apolloTracing

  val interpreter = api.interpreter.map(errorHandler(_))

  private def errorHandler[R](
      interpreter: GraphQLInterpreter[R, CalibanError]
  ): GraphQLInterpreter[R, CalibanError] = interpreter.mapError {
    case err @ ExecutionError(_, _, _, Some(u: Unauthorized), _)  =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("UNAUTHORIZED"),
            "message"   -> StringValue(u.getMessage)
          ))))
    case err @ ExecutionError(_, _, _, Some(n: Forbidden), _)     =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("FORBIDDEN"),
            "message"   -> StringValue(n.getMessage)
          ))))
    case err @ ExecutionError(_, _, _, Some(n: InvalidEntity), _) =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("INVALID_ENTITY"),
            "message"   -> StringValue(n.getMessage)
          ))))
    case err @ ExecutionError(_, _, _, Some(e: Throwable), _)     =>
      err.copy(extensions = Some(
        ObjectValue(
          List(
            "errorCode" -> StringValue("SERVER_ERROR"),
            "errorType" -> StringValue(e.getClass.toString),
            "message"   -> StringValue(e.getMessage)
          ))))
    case err: ExecutionError                                      =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("EXECUTION_ERROR")))))
    case err: ValidationError                                     =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("VALIDATION_ERROR")))))
    case err: ParsingError                                        =>
      err.copy(extensions = Some(ObjectValue(List("errorCode" -> StringValue("PARSING_ERROR")))))
  }
}

// TODO: incorporate pagination.
sealed trait Pagination

case object All extends Pagination

case class Offset(first: Int, from: Int) extends Pagination
