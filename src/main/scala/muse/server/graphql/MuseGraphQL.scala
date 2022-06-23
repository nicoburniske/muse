package muse.server.graphql

import caliban.schema.{GenericSchema, Schema}
import caliban.wrappers.Wrappers.printErrors
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.{GraphQL, RootResolver}
import muse.domain.common.EntityType
import muse.domain.mutate.{CreateComment, CreateReview, UpdateComment, UpdateReview}
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.domain.spotify.AlbumType
import muse.domain.tables.ReviewComment
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
import muse.service.persist.DatabaseQueries
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
  given userSchema: Schema[DatabaseQueries & SpotifyService, User] = Schema.gen

  given reviewSchema: Schema[DatabaseQueries & SpotifyService, Review] = Schema.gen

  given commentsSchema: Schema[DatabaseQueries & SpotifyService, Comment] = Schema.gen

  given entitySchema: Schema[SpotifyService, ReviewEntity] = Schema.gen

  given albumSchema: Schema[SpotifyService, Album] = Schema.gen

  given artistSchema: Schema[SpotifyService, Artist] = Schema.gen

  given playlistSchema: Schema[SpotifyService, Playlist] = Schema.gen

  given trackSchema: Schema[SpotifyService, Track] = Schema.gen

  given userArgs: Schema[DatabaseQueries, UserArgs] = Schema.gen

  given createReview: Schema[Auth[UserSession] & DatabaseQueries, CreateReview] = Schema.gen

  given createComment: Schema[Auth[UserSession] & DatabaseQueries, CreateComment] = Schema.gen

  given updateReview: Schema[Auth[UserSession] & DatabaseQueries, UpdateReview] = Schema.gen

  given updateComment: Schema[Auth[UserSession] & DatabaseQueries, UpdateComment] = Schema.gen

  given searchSchema: Schema[SpotifyService, SearchResult] = Schema.gen

  type Env = Auth[UserSession] & DatabaseQueries & SpotifyService
  val api =
    GraphQL.graphQL[Env, Queries, Mutations, Unit](
      RootResolver(Queries.live, Mutations.live)) @@ printErrors @@ apolloTracing
}

// TODO: incorporate pagination.
sealed trait Pagination

case object All extends Pagination

case class Offset(first: Int, from: Int) extends Pagination
