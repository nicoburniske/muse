package muse.service.persist

import io.getquill.*
import io.getquill.context.ZioJdbc.*
import muse.domain.common.EntityType
import muse.domain.mutate.{CreateComment, CreateReview, DeleteComment, DeleteReview, ShareReview, UpdateComment, UpdateReview}
import muse.domain.table.{AccessLevel, AppUser, Review, ReviewAccess, ReviewComment}
import zio.ZLayer.*
import zio.{IO, Schedule, TaskLayer, ZIO, ZLayer, durationInt}

import java.sql.{SQLException, Timestamp, Types}
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

trait DatabaseService {

  /**
   * Create!
   */
  def createUser(userId: String): IO[SQLException, Unit]
  def createReview(id: String, review: CreateReview): IO[SQLException, Review]
  def createReviewComment(id: String, review: CreateComment): IO[SQLException, ReviewComment]

  /**
   * Read!
   */
  def getUsers: IO[SQLException, List[AppUser]]
  def getUserById(userId: String): IO[SQLException, Option[AppUser]]
  // Reviews that the given user created.
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  // Reviews that the given user has access to.
  def getAllUserReviews(userId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]
  def getMultiReviewComments(reviewIds: List[UUID]): IO[SQLException, List[ReviewComment]]
  def getReviews(reviewIds: List[UUID]): IO[SQLException, List[Review]]
  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]
  def getUsersWithAccess(reviewId: UUID): IO[SQLException, List[ReviewAccess]]

  /**
   * Update!
   */
  def updateReview(review: UpdateReview): IO[SQLException, Boolean]
  def updateComment(comment: UpdateComment): IO[SQLException, Boolean]
  def shareReview(share: ShareReview): IO[SQLException, Boolean]

  /**
   * Delete!
   *
   * Returns whether a record was deleted.
   */
  def deleteReview(d: DeleteReview): IO[SQLException, Boolean]
  def deleteComment(d: DeleteComment): IO[SQLException, Boolean]

  /**
   * Permissions!
   */
  def canMakeComment(userId: String, reviewId: UUID): IO[SQLException, Boolean]
  def canViewReview(userId: String, reviewId: UUID): IO[SQLException, Boolean]
  def canModifyReview(userId: String, reviewId: UUID): IO[SQLException, Boolean]
  def canModifyComment(userId: String, reviewId: UUID, commentId: Int): IO[SQLException, Boolean]

  // TODO: sharing methods.

}

object DatabaseService {
  val layer = ZLayer.fromFunction(DatabaseServiceLive.apply(_))

  def createUser(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.createUser(userId))

  def createReview(userId: String, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.createReview(userId, review))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserById(userId))

  def getReview(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReview(reviewId))

  def getUsers = ZIO.serviceWithZIO[DatabaseService](_.getUsers)

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getAllUserReviews(userId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewComments(reviewId))

  def getAllReviewComments(reviewIds: List[UUID]) =
    ZIO.serviceWithZIO[DatabaseService](_.getMultiReviewComments(reviewIds))

  def getUsersWithAccess(reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.getUsersWithAccess(reviewId))

  def updateReview(review: UpdateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReview(review))

  def updateComment(comment: UpdateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.updateComment(comment))

  def shareReview(share: ShareReview) =
    ZIO.serviceWithZIO[DatabaseService](_.shareReview(share))

  def deleteReview(d: DeleteReview) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteReview(d))

  def deleteComment(d: DeleteComment) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteComment(d))

  def canViewReview(userId: String, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canViewReview(userId, reviewId))

  def canModifyReview(userId: String, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyReview(userId, reviewId))

  def canModifyComment(userId: String, reviewId: UUID, commentId: Int) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyComment(userId, reviewId, commentId))

  def canMakeComment(userId: String, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canMakeComment(userId, reviewId))

  /**
   * If username already exists, update existing row's auth information. Otherwise create user.
   *
   * @param userId
   *   current user request
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def createOrUpdateUser(userId: String) =
    getUserById(userId).map(_.isDefined).flatMap {
      case true  => ZIO.succeed(false)
      case false => createUser(userId).as(true)
    }

  /**
   * TODO: FIX BELOW?
   */
  enum ReviewOptions:
    case UserOwnedReviewsPublic, UserOwnedReviewsPrivate, UserOwnedReviewsAll, UserAccessReviews

  def userReviewsOptions(userId: String, options: ReviewOptions) = options match
    case ReviewOptions.UserOwnedReviewsPublic  =>
      DatabaseService.getUserReviews(userId).map(_.filter(_.isPublic))
    case ReviewOptions.UserOwnedReviewsPrivate =>
      DatabaseService.getUserReviews(userId).map(_.filterNot(_.isPublic))
    case ReviewOptions.UserOwnedReviewsAll     =>
      DatabaseService.getUserReviews(userId)
    case ReviewOptions.UserAccessReviews       =>
      DatabaseService.getAllUserReviews(userId)

}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  // Exponential backoff retry strategy for connecting to Postgres DB.
  val schedule        = Schedule.exponential(1.second) && Schedule.recurs(10)
  val dataSourceLayer = DataSourceLayer.fromPrefix("database").retry(schedule)

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
}

final case class DatabaseServiceLive(d: DataSource) extends DatabaseService {

  import QuillContext.{*, given}

  val layer = ZLayer.succeed(d)

  inline def users = querySchema[AppUser]("muse.app_user")

  inline def reviewAccess = querySchema[ReviewAccess]("muse.review_access")

  inline def reviews = querySchema[Review]("muse.review")

  inline def comments = querySchema[ReviewComment]("muse.review_comment")

  /**
   * Read!
   */

  inline def userReviews(inline userId: String) = reviews.filter(_.creatorId == userId)

  inline def userSharedReviews(inline userId: String) =
    reviewAccess
      .filter(_.userId == userId)
      .rightJoin(reviews)
      .on((access, review) => review.id == access.reviewId)
      .map(_._2)

  override def getAllUserReviews(userId: String) = run {
    userReviews(lift(userId)).union(userSharedReviews(lift(userId)))
  }.provide(layer)

  override def getUsers = run(users).provide(layer)

  override def getReview(reviewId: UUID) = run {
    reviews.filter(_.id == lift(reviewId))
  }.map(_.headOption).provide(layer)

  override def getUserById(userId: String) = run {
    users.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  override def getUserReviews(userId: String) =
    run(userReviews(lift(userId))).provide(layer)

  override def getReviewComments(reviewId: UUID) = run {
    comments.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  override def getMultiReviewComments(reviewIds: List[UUID]) = run {
    comments.filter(c => liftQuery(reviewIds.toSet).contains(c.reviewId))
  }.provideLayer(layer)

  override def getReviews(reviewIds: List[UUID]) = run {
    reviews.filter(c => liftQuery(reviewIds.toSet).contains(c.id))
  }.provideLayer(layer)

  override def getUsersWithAccess(reviewId: UUID) = run {
    reviewAccess.filter(_.reviewId == lift(reviewId))
  }.provideLayer(layer)

  /**
   * Create!
   */

  override def createUser(userId: String) = run {
    users.insert(
      _.id -> lift(userId)
    )
  }.provideLayer(layer).unit

  override def createReview(userId: String, review: CreateReview) = run {
    reviews
      .insert(
        _.creatorId  -> lift(userId),
        _.reviewName -> lift(review.name),
        _.isPublic   -> lift(review.isPublic),
        _.entityType -> lift(review.entityType),
        _.entityId   -> lift(review.entityId)
      )
      .returningGenerated(r => r.id -> r.createdAt)
  }.provide(layer).map {
    case (uuid, instant) =>
      Review(uuid, instant, userId, review.name, review.isPublic, review.entityType, review.entityId)
  }

  override def createReviewComment(userId: String, c: CreateComment) = run {
    comments
      .insert(
        _.reviewId        -> lift(c.reviewId),
        _.commenter       -> lift(userId),
        _.parentCommentId -> lift(c.parentCommentId),
        _.comment         -> lift(c.comment),
        _.rating          -> lift(c.rating),
        _.entityType      -> lift(c.entityType),
        _.entityId        -> lift(c.entityId)
      )
      .returningGenerated(c => (c.id, c.createdAt, c.updatedAt))
  }.provide(layer).map {
    case (id, created, updated) =>
      ReviewComment(id, c.reviewId, created, updated, c.parentCommentId, userId, c.comment, c.rating, c.entityType, c.entityId)
  }

  /**
   * Update!
   */
  override def updateReview(r: UpdateReview) = run {
    reviews
      .filter(_.id == lift(r.reviewId))
      .update(
        _.reviewName -> lift(r.name),
        _.isPublic   -> lift(r.isPublic)
      )
  }.provide(layer)
    .map(_ > 0)

  override def updateComment(c: UpdateComment) = run {
    comments
      .filter(_.id == lift(c.commentId))
      .filter(_.reviewId == lift(c.reviewId))
      .update(
        _.comment -> lift(c.comment),
        _.rating  -> lift(c.rating)
      )
  }.provide(layer)
    .map(_ > 0)

  override def shareReview(share: ShareReview) = run {
    reviewAccess.insert(
      _.reviewId    -> lift(share.reviewId),
      _.userId      -> lift(share.userId),
      _.accessLevel -> lift(share.access)
    )
  }.provide(layer)
    .map(_ > 0)

  /**
   * Delete!
   */
  def deleteReview(delete: DeleteReview) = run {
    reviews.filter(_.id == lift(delete.id)).delete
  }.provide(layer)
    .map(_ > 0)

  def deleteComment(d: DeleteComment) = run {
    comments
      .filter(_.id == lift(d.commentId))
      .filter(_.reviewId == lift(d.reviewId))
      .delete
  }.provide(layer)
    .map(_ > 0)

  /**
   * Permissions logic.
   */

  inline def reviewCreator(inline reviewId: UUID): EntityQuery[String] =
    reviews.filter(_.id == reviewId).map(_.creatorId)

  inline def usersWithAccess(inline reviewId: UUID): EntityQuery[String] =
    reviewAccess.filter(_.reviewId == reviewId).map(_.userId)

  inline def allReviewUsersWithViewAccess(inline reviewId: UUID): Query[String] =
    reviewCreator(reviewId) union usersWithAccess(reviewId)

  inline def usersWithWriteAccess(inline reviewId: UUID): EntityQuery[String] =
    reviewAccess
      .filter(_.reviewId == reviewId)
      .filter(_.accessLevel == lift(AccessLevel.Collaborator))
      .map(_.userId)

  inline def allUsersWithWriteAccess(inline reviewId: UUID) =
    reviewCreator(reviewId) union usersWithWriteAccess(reviewId)

  inline def isReviewPublic(inline reviewId: UUID): EntityQuery[Boolean] =
    reviews.filter(_.id == lift(reviewId)).map(_.isPublic)

  // TODO: can this be more efficient / Single query?
  override def canViewReview(userId: String, reviewId: UUID) =
    run(isReviewPublic(reviewId))
      .map(_.headOption)
      .flatMap {
        case Some(true)  => ZIO.succeed(true)
        case Some(false) =>
          run {
            allReviewUsersWithViewAccess(lift(reviewId))
              .filter(_ == lift(userId))
          }.map(_.nonEmpty)
        case _           =>
          // TODO include logic for NotFoundException?
          throw new RuntimeException("")
      }
      .provide(layer)

  override def canModifyReview(userId: String, reviewId: UUID) = run {
    reviewCreator(lift(reviewId)).contains(lift(userId))
  }.provide(layer)

  override def canModifyComment(userId: String, reviewId: UUID, commentId: Int) = run {
    comments
      .filter(_.id == lift(commentId))
      .filter(_.reviewId == lift(reviewId))
      .map(_.commenter)
      .contains(lift(userId))
  }.provide(layer)

  override def canMakeComment(userId: String, reviewId: UUID): IO[SQLException, Boolean] = run {
    allUsersWithWriteAccess(lift(reviewId)).filter(_ == lift(userId))
  }.map(_.nonEmpty).provide(layer)

}
