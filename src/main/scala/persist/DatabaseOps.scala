package persist

import io.getquill.context.ZioJdbc._
import zio._
import io.getquill._
import zio.ZLayer._
import zio.Console.printLine

import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource
import java.util.UUID
import org.scalameta.data.data
import javax.xml.crypto.Data
import java.sql.Types
import java.sql.Timestamp

final case class AppUser(
    id: String
)

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    isPublic: Boolean,
    entityType: Int,
    entityId: String
)

final case class ReviewAccess(
    reviewId: UUID,
    userId: String
)

final case class ReviewComment(
    // GUID?
    id: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenter: String,
    comment: Option[String],
    rating: Option[Int],
    entityType: Int,
    entityId: Int
)

final case class NewReview(creatorId: String, isPublic: Boolean, entityType: Int, entityId: String)

final case class NewReviewComment(
    reviewID: UUID,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenter: String,
    comment: Option[String],
    rating: Option[Int],
    entityType: Int,
    entityId: Int
)

trait DatabaseQueries {
  def getUsers: IO[SQLException, List[AppUser]]
  def getUserById(userId: String): IO[SQLException, Option[AppUser]]
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]

  def createReview(review: NewReview): IO[SQLException, Unit]
  def createReviewComment(review: NewReviewComment): IO[SQLException, Unit]
}

object DatabaseQueries {
  val live = ZLayer(for { a <- ZIO.service[DataSource] } yield DataServiceLive(a))

  def getUserById(userId: String)       = ZIO.serviceWithZIO[DatabaseQueries](_.getUserById(userId))
  def getUsers                          = ZIO.serviceWithZIO[DatabaseQueries](_.getUsers)
  def getUserReviews(userId: String)    = ZIO.serviceWithZIO[DatabaseQueries](_.getUserReviews(userId))
  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseQueries](_.getReviewComments(reviewId))
  def createReview(review: NewReview)   = ZIO.serviceWithZIO[DatabaseQueries](_.createReview(review))
}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  given instantDecoder: Decoder[Instant] = decoder((index, row, session) => row.getTimestamp(index).toInstant)
  given instantEncoder: Encoder[Instant] =
    encoder(Types.TIMESTAMP, (idx, value, row) => row.setTimestamp(idx, Timestamp.from(value)))

  val dataSourceLayer: ULayer[DataSource] = DataSourceLayer.fromPrefix("database").orDie
}

final case class DataServiceLive(d: DataSource) extends DatabaseQueries {
  import QuillContext.{*, given}
  val layer = ZLayer.fromFunction(() => d)

  inline def users    = query[AppUser]
  inline def reviews  = query[Review]
  inline def comments = query[ReviewComment]

  def getUsers = run(users).provide(layer)

  def getUserById(userId: String) = run {
    users.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  def getUserReviews(userId: String) = run {
    reviews.filter(_.creatorId == lift(userId))
  }.provide(layer)

  def getReviewComments(reviewId: UUID) = run {
    comments.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  def createReview(review: NewReview) = run {
    reviews.insert(
      _.creatorId  -> lift(review.creatorId),
      _.isPublic   -> lift(review.isPublic),
      _.entityType -> lift(review.entityType),
      _.entityId   -> lift(review.entityId)
    )
  }.provide(layer).unit

  def createReviewComment(c: NewReviewComment) = run {
    comments.insert(
      _.reviewId        -> lift(c.reviewID),
      _.commenter       -> lift(c.commenter),
      _.parentCommentId -> lift(c.parentCommentId),
      _.comment         -> lift(c.comment),
      _.rating          -> lift(c.rating),
      _.entityType      -> lift(c.entityType),
      _.entityId        -> lift(c.entityId)
    )
  }.provide(layer).unit
}
