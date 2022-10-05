package muse.server.graphql

import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import caliban.schema.{GenericSchema, Schema}
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.printErrors
import caliban.{CalibanError, GraphQL, GraphQLInterpreter, RootResolver}
import muse.domain.common.EntityType
import muse.domain.error.{Forbidden, InvalidEntity, InvalidUser, MuseError, Unauthorized}
import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.domain.spotify.AlbumType
import muse.domain.table.ReviewComment
import muse.server.graphql.subgraph.{Album, Artist, Comment, PlaybackState, Playlist, PlaylistTrack, Review, ReviewEntity, SearchResult, Track, User}
import muse.service.RequestSession
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyError, SpotifyService}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import scala.util.Try

object MuseGraphQL {
  type Env = RequestSession[UserSession] & DatabaseService & RequestSession[SpotifyService]

  given userSchema: Schema[Env, User] = Schema.gen

  given reviewSchema: Schema[Env, Review] = Schema.gen

  given commentsSchema: Schema[Env, Comment] = Schema.gen

  given entitySchema: Schema[Env, ReviewEntity] = Schema.gen

  given playlistSchema: Schema[Env, Playlist] = Schema.gen

  given playlistTrackSchema: Schema[Env, PlaylistTrack] = Schema.gen

  given albumSchema: Schema[Env, Album] = Schema.gen

  given artistSchema: Schema[Env, Artist] = Schema.gen

  given trackSchema: Schema[Env, Track] = Schema.gen

  // TODO: give this another shot?
//  given errorSchema[A](using Schema[Any, A]): Schema[Any, IO[Throwable | MuseError, A]] =
//    Schema.customErrorEffectSchema((e: Throwable | MuseError) =>
//      e.match
//        case museError: MuseError => ExecutionError(museError.message, innerThrowable = Some(MuseThrowable(museError)))
//        case throwable: Throwable => ExecutionError(throwable.getMessage, innerThrowable = Some(throwable))
//    )

  val api = GraphQL.graphQL[Env, Queries, Mutations, Subscriptions](
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
