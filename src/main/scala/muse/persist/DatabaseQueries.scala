package muse.persist

import muse.domain.common.EntityType
import io.getquill.context.ZioJdbc.*
import zio.*
import io.getquill.*
import zio.ZLayer.*
import zio.Console.printLine

import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource
import java.util.UUID

import java.sql.Types
import java.sql.Timestamp
import muse.domain.tables.{AppUser, Review, ReviewAccess, ReviewComment}
import muse.domain.create.{CreateComment, CreateReview}

trait DatabaseQueries {

  def createUser(user: AppUser): IO[SQLException, Unit]
  def createReview(id: String, review: CreateReview): IO[SQLException, Unit]
  def createReviewComment(id: String, review: CreateComment): IO[SQLException, Unit]

  def getUsers: IO[SQLException, List[AppUser]]
  def getUserById(userId: String): IO[SQLException, Option[AppUser]]
  // Reviews that the given user created.
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  // Reviews that the given user has access to.
  def getAllUserReviews(userId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]

  def updateUser(user: AppUser): IO[SQLException, Unit]
  // def updateReview(review: NewReview)
}

object DatabaseQueries {
  val live = ZLayer(for { ds <- ZIO.service[DataSource] } yield DataServiceLive(ds))

  def createUser(user: AppUser) = ZIO.serviceWithZIO[DatabaseQueries](_.createUser(user))

  def createReview(userId: String, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseQueries](_.createReview(userId, review))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseQueries](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getUserById(userId))

  def getUsers = ZIO.serviceWithZIO[DatabaseQueries](_.getUsers)

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getAllUserReviews(userId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseQueries](_.getReviewComments(reviewId))

  def updateUser(user: AppUser) = ZIO.serviceWithZIO[DatabaseQueries](_.updateUser(user))
}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  given instantDecoder: Decoder[Instant] = decoder((index, row, session) => row.getTimestamp(index).toInstant)
  given instantEncoder: Encoder[Instant] =
    encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, Timestamp.from(value)))

  given entityTypeDecoder: Decoder[EntityType] =
    decoder((index, row, session) => EntityType.fromOrdinal(row.getInt(index)))
  given entityTypeEncoder: Encoder[EntityType] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

  // TODO: move this somewhere else
  val dataSourceLayer: ULayer[DataSource] = DataSourceLayer.fromPrefix("database").orDie
}

final case class DataServiceLive(d: DataSource) extends DatabaseQueries {
  import QuillContext.{*, given}
  val layer = ZLayer.fromFunction(() => d)

  inline def users        = query[AppUser]
  inline def reviewAccess = query[ReviewAccess]
  inline def reviews      = query[Review]
  inline def comments     = query[ReviewComment]

  inline def getUserReviewsQuery(userId: String) = reviews.filter(_.creatorId == lift(userId))

  inline def getUserSharedReviewsQuery(userId: String) =
    reviewAccess
      .filter(_.userId == lift(userId))
      .rightJoin(reviews)
      .on((access, review) => review.id == access.reviewId)
      .map(_._2)

  def getAllUserReviews(userId: String) = run {
    getUserReviewsQuery(userId).union(getUserSharedReviewsQuery(userId))
  }.provide(layer)

  def getUsers = run(users).provide(layer)

  def getUserById(userId: String) = run {
    users.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  def getUserReviews(userId: String) =
    run(getUserReviewsQuery(userId)).provide(layer)

  def getReviewComments(reviewId: UUID) = run {
    comments.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  def createUser(user: AppUser) = run {
    users.insert(
      _.id           -> lift(user.id),
      _.accessToken  -> lift(user.accessToken),
      _.refreshToken -> lift(user.refreshToken)
    )
  }.provideLayer(layer).unit

  def createReview(userId: String, review: CreateReview) = run {
    reviews.insert(
      _.creatorId  -> lift(userId),
      _.reviewName -> lift(review.name),
      _.isPublic   -> lift(review.isPublic),
      _.entityType -> lift(review.entityType),
      _.entityId   -> lift(review.entityId)
    )
  }.provide(layer).unit

  def createReviewComment(id: String, c: CreateComment) = run {
    comments.insert(
      _.reviewId        -> lift(c.reviewID),
      _.commenter       -> lift(id),
      _.parentCommentId -> lift(c.parentCommentId),
      _.comment         -> lift(c.comment),
      _.rating          -> lift(c.rating),
      _.entityType      -> lift(c.entityType),
      _.entityId        -> lift(c.entityId)
    )
  }.provide(layer).unit

  def updateUser(user: AppUser) = run {
    users.filter(_.id == lift(user.id)).updateValue(lift(user))
  }.provide(layer).unit
}
