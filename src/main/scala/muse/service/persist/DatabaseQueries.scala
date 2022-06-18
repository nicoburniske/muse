package muse.service.persist

import io.getquill.*
import io.getquill.context.ZioJdbc.*
import muse.domain.common.EntityType
import muse.domain.create.{CreateComment, CreateReview}
import muse.domain.tables.{AccessLevel, AppUser, Review, ReviewAccess, ReviewComment}
import zio.*
import zio.Console.printLine
import zio.ZLayer.*

import java.sql.{SQLException, Timestamp, Types}
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

trait DatabaseQueries {

  def createUser(userId: String): IO[SQLException, Unit]
  def createReview(id: String, review: CreateReview): IO[SQLException, Unit]
  def createReviewComment(id: String, review: CreateComment): IO[SQLException, Unit]

  def getUsers: IO[SQLException, List[AppUser]]
  def getUserById(userId: String): IO[SQLException, Option[AppUser]]
  // Reviews that the given user created.
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  // Reviews that the given user has access to.
  def getAllUserReviews(userId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]
  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]

  // def updateUser(user: AppUser): IO[SQLException, Unit]
  // def updateReview(review: NewReview)
}

object DatabaseQueries {
  val live = ZLayer(for { ds <- ZIO.service[DataSource] } yield DataServiceLive(ds))

  def createUser(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.createUser(userId))

  def createReview(userId: String, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseQueries](_.createReview(userId, review))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseQueries](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getUserById(userId))

  def getReview(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseQueries](_.getReview(reviewId))

  def getUsers = ZIO.serviceWithZIO[DatabaseQueries](_.getUsers)

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseQueries](_.getAllUserReviews(userId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseQueries](_.getReviewComments(reviewId))

  //  def updateUser(user: String) = ZIO.serviceWithZIO[DatabaseQueries](_.updateUser(user))
}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  given instantDecoder: Decoder[Instant] = decoder((index, row, session) => row.getTimestamp(index).toInstant)

  given instantEncoder: Encoder[Instant] =
    encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, Timestamp.from(value)))

  given entityTypeDecoder: Decoder[EntityType] =
    decoder((index, row, session) => EntityType.fromOrdinal(row.getInt(index)))

  given entityTypeEncoder: Encoder[EntityType] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

  given reviewAccessDecoder: Decoder[AccessLevel] =
    decoder((index, row, session) => AccessLevel.fromOrdinal(row.getInt(index)))

  given reviewAccessEncoder: Encoder[AccessLevel] =
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

  def getReview(reviewId: UUID) = run {
    reviews.filter(_.id == lift(reviewId))
  }.map(_.headOption).provide(layer)

  def getUserById(userId: String) = run {
    users.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  def getUserReviews(userId: String) =
    run(getUserReviewsQuery(userId)).provide(layer)

  def getReviewComments(reviewId: UUID) = run {
    comments.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  def createUser(userId: String) = run {
    users.insert(
      _.id -> lift(userId)
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
