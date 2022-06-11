package persist

import domain.common.EntityType
import io.getquill.context.ZioJdbc.*
import zio.*
import io.getquill.*
import zio.ZLayer.*
import zio.Console.printLine

import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource
import java.util.UUID
import org.scalameta.data.data

import javax.xml.crypto.Data
import java.sql.Types
import java.sql.Timestamp
import domain.tables.{AppUser, Review, ReviewComment}
import domain.create.{CreateComment, CreateReview}

trait DatabaseQueries {
  def getUsers: IO[SQLException, List[AppUser]]
  def getUserById(userId: String): IO[SQLException, Option[AppUser]]
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]

  def createReview(review: CreateReview): IO[SQLException, Unit]
  def createReviewComment(review: CreateComment): IO[SQLException, Unit]

  // def updateReview(review: NewReview)
}

object DatabaseQueries {
  val live = ZLayer(for { a <- ZIO.service[DataSource] } yield DataServiceLive(a))

  def getUserById(userId: String)       = ZIO.serviceWithZIO[DatabaseQueries](_.getUserById(userId))
  def getUsers                          = ZIO.serviceWithZIO[DatabaseQueries](_.getUsers)
  def getUserReviews(userId: String)    = ZIO.serviceWithZIO[DatabaseQueries](_.getUserReviews(userId))
  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseQueries](_.getReviewComments(reviewId))

  def createReview(review: CreateReview)    = ZIO.serviceWithZIO[DatabaseQueries](_.createReview(review))
  def createReviewComment(c: CreateComment) = ZIO.serviceWithZIO[DatabaseQueries](_.createReviewComment(c))

}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  given instantDecoder: Decoder[Instant] = decoder((index, row, session) => row.getTimestamp(index).toInstant)
  given instantEncoder: Encoder[Instant] =
    encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, Timestamp.from(value)))

  given entityTypeDecoder: Decoder[EntityType] =
    decoder((index, row, session) => EntityType.fromOrdinal(row.getInt(index)))
  given entityTypeEncoder: Encoder[EntityType] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

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

  def getUserReviewsQuery(userId: String) = run {
    reviews.filter(_.creatorId == lift(userId))
  }

  def getUserReviews(userId: String) = getUserReviewsQuery(userId).provide(layer)

  def getReviewComments(reviewId: UUID) = run {
    comments.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  def createReview(review: CreateReview) = run {
    reviews.insert(
      _.creatorId  -> lift(review.creatorId),
      _.isPublic   -> lift(review.isPublic),
      _.entityType -> lift(review.entityType),
      _.entityId   -> lift(review.entityId)
    )
  }.provide(layer).unit

  def createReviewComment(c: CreateComment): IO[SQLException, Unit] = run {
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
