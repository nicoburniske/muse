package muse.service.persist

import io.getquill.*
import io.getquill.context.ZioJdbc.*
import muse.domain.common.EntityType
import muse.domain.mutate.{CreateComment, CreateReview, DeleteComment, DeleteReview, ShareReview, UpdateComment, UpdateReview}
import muse.domain.table.{AccessLevel, Review, ReviewAccess, ReviewComment, User, UserSession}
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
  def createUserSession(sessionId: String, refreshToken: String, userId: String): IO[SQLException, Unit]
  def createReview(id: String, review: CreateReview): IO[SQLException, Review]
  def createReviewComment(id: String, review: CreateComment): IO[SQLException, ReviewComment]

  /**
   * Read!
   */
  def getUsers: IO[SQLException, List[User]]
  def getUserById(userId: String): IO[SQLException, Option[User]]
  def getUserSession(sessionId: String): IO[SQLException, Option[UserSession]]
  // Reviews that the given user created.
  def getUserReviews(userId: String): IO[SQLException, List[Review]]
  // Reviews that the given user has access to.
  def getAllUserReviews(userId: String): IO[SQLException, List[Review]]
  // Reviews that viewerUser can see of sourceUser.
  def getUserReviewsExternal(sourceUserId: String, viewerUserId: String): IO[SQLException, List[Review]]
  def getReviewComments(reviewId: UUID): IO[SQLException, List[ReviewComment]]
  def getMultiReviewComments(reviewIds: List[UUID]): IO[SQLException, List[ReviewComment]]
  def getReviews(reviewIds: List[UUID]): IO[SQLException, List[Review]]
  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]
  def getUsersWithAccess(reviewId: UUID): IO[SQLException, List[ReviewAccess]]

  /**
   * Update!
   */
  def updateReview(review: UpdateReview): IO[SQLException, Review]
  def updateComment(comment: UpdateComment): IO[SQLException, ReviewComment]
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

}

object DatabaseService {
  val layer = ZLayer.fromFunction(DatabaseServiceLive.apply(_))

  def createUser(userId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.createUser(userId))

  def createUserSession(sessionId: String, refreshToken: String, userId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.createUserSession(sessionId, refreshToken, userId))

  def createReview(userId: String, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.createReview(userId, review))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserById(userId))

  def getUserSession(sessionId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserSession(sessionId))

  def getReview(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReview(reviewId))

  def getUsers = ZIO.serviceWithZIO[DatabaseService](_.getUsers)

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getAllUserReviews(userId))

  def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserReviewsExternal(sourceUserId, viewerUserId))

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

  def createOrUpdateUser(sessionId: String, refreshToken: String, userId: String) =
    createUser(userId)
      *> createUserSession(sessionId, refreshToken, userId)

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

  inline def user = querySchema[User]("muse.user")

  inline def userSession = querySchema[UserSession]("muse.user_session")

  inline def reviewAccess = querySchema[ReviewAccess]("muse.review_access")

  inline def review = querySchema[Review]("muse.review")

  inline def comment = querySchema[ReviewComment]("muse.review_comment")

  /**
   * Read!
   */

  inline def userReviews(inline userId: String) = review.filter(_.creatorId == userId)

  inline def userSharedReviews(inline userId: String) =
    reviewAccess
      .filter(_.userId == userId)
      .leftJoin(review)
      .on((access, review) => review.id == access.reviewId)
      .map(_._2)

  inline def allUserReviews(inline userId: String) =
    userReviews(userId).map(Some(_)) union userSharedReviews(userId)

  override def getAllUserReviews(userId: String) = run {
    allUserReviews(lift(userId))
  }.provide(layer)
    .map(_.flatten)

  override def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) = run {
    userReviews(lift(sourceUserId)).filter(_.isPublic).map(Some(_)) union
      userSharedReviews(lift(viewerUserId)).filter(_.exists(_.creatorId == lift(sourceUserId)))
  }.provide(layer)
    .map(_.flatten)

  override def getUsers = run(user).provide(layer)

  override def getReview(reviewId: UUID) = run {
    review.filter(_.id == lift(reviewId))
  }.map(_.headOption).provide(layer)

  override def getUserById(userId: String) = run {
    user.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  override def getUserSession(sessionId: String) = run {
    userSession.filter(_.sessionId == lift(sessionId))
  }.map(_.headOption).provide(layer)

  override def getUserReviews(userId: String) =
    run(userReviews(lift(userId))).provide(layer)

  override def getReviewComments(reviewId: UUID) = run {
    comment.filter(_.reviewId == lift(reviewId))
  }.provide(layer)

  override def getMultiReviewComments(reviewIds: List[UUID]) = run {
    comment.filter(c => liftQuery(reviewIds.toSet).contains(c.reviewId))
  }.provideLayer(layer)

  override def getReviews(reviewIds: List[UUID]) = run {
    review.filter(c => liftQuery(reviewIds.toSet).contains(c.id))
  }.provideLayer(layer)

  override def getUsersWithAccess(reviewId: UUID) = run {
    reviewAccess.filter(_.reviewId == lift(reviewId))
  }.provideLayer(layer)

  /**
   * Create!
   */

  override def createUser(userId: String) = run {
    user
      .insert(
        _.id -> lift(userId)
      ).onConflictIgnore
  }.provideLayer(layer).unit

  def createUserSession(sessionId: String, refreshToken: String, userId: String) = run {
    userSession.insert(
      _.sessionId    -> lift(sessionId),
      _.refreshToken -> lift(refreshToken),
      _.userId       -> lift(userId)
    )
  }.provideLayer(layer).unit

  override def createReview(userId: String, create: CreateReview) = run {
    review
      .insert(
        _.creatorId  -> lift(userId),
        _.reviewName -> lift(create.name),
        _.isPublic   -> lift(create.isPublic),
        _.entityType -> lift(create.entityType),
        _.entityId   -> lift(create.entityId)
      )
      .returningGenerated(r => r.id -> r.createdAt)
  }.provide(layer).map {
    case (uuid, instant) =>
      Review(uuid, instant, userId, create.name, create.isPublic, create.entityType, create.entityId)
  }

  override def createReviewComment(userId: String, c: CreateComment) = run {
    comment
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
    review
      .filter(_.id == lift(r.reviewId))
      .update(
        _.reviewName -> lift(r.name),
        _.isPublic   -> lift(r.isPublic)
      ).returning(r => r)
  }.provide(layer)

  override def updateComment(c: UpdateComment) = ZIO
    .succeed(Instant.now()).flatMap { now =>
      run {
        comment
          .filter(_.id == lift(c.commentId))
          .filter(_.reviewId == lift(c.reviewId))
          .update(
            _.comment   -> lift(c.comment),
            _.rating    -> lift(c.rating),
            _.updatedAt -> lift(now)
          ).returning(r => r)
      }
    }.provide(layer)

  override def shareReview(share: ShareReview) =
    (share.accessLevel match
      // Delete.
      case None              =>
        run {
          reviewAccess
            .filter(_.reviewId == lift(share.reviewId))
            .filter(_.userId == lift(share.userId))
            .delete
        }
      // Insert/Update.
      case Some(accessLevel) =>
        run {
          reviewAccess
            .insert(
              _.reviewId    -> lift(share.reviewId),
              _.userId      -> lift(share.userId),
              _.accessLevel -> lift(accessLevel)
              // On conflict, update existing entry with new access level.
            ).onConflictUpdate(_.reviewId, _.userId)((t, e) => t.accessLevel -> e.accessLevel)
        }
    )
      .provide(layer)
      .map(_ > 0)

  /**
   * Delete!
   */
  def deleteReview(delete: DeleteReview) = run {
    review.filter(_.id == lift(delete.id)).delete
  }.provide(layer)
    .map(_ > 0)

  def deleteComment(d: DeleteComment) = run {
    comment
      .filter(_.id == lift(d.commentId))
      .filter(_.reviewId == lift(d.reviewId))
      .delete
  }.provide(layer)
    .map(_ > 0)

  /**
   * Permissions logic.
   */

  inline def reviewCreator(inline reviewId: UUID): EntityQuery[String] =
    review.filter(_.id == reviewId).map(_.creatorId)

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

  // TODO: test this!
  override def canViewReview(userId: String, reviewId: UUID) =
    run {
      // Is review public?
      (review.filter(_.isPublic).map(Some(_))
        union
          // Or does user have access to it?
          allUserReviews(lift(userId)))
        .map(_.map(_.id))
        .filter(_.exists(_ == lift(reviewId)))
        .nonEmpty
    }.provide(layer)

  override def canModifyReview(userId: String, reviewId: UUID) = run {
    reviewCreator(lift(reviewId)).contains(lift(userId))
  }.provide(layer)

  override def canModifyComment(userId: String, reviewId: UUID, commentId: Int) = run {
    comment
      .filter(_.id == lift(commentId))
      .filter(_.reviewId == lift(reviewId))
      .map(_.commenter)
      .contains(lift(userId))
  }.provide(layer)

  override def canMakeComment(userId: String, reviewId: UUID): IO[SQLException, Boolean] = run {
    allUsersWithWriteAccess(lift(reviewId)).filter(_ == lift(userId))
  }.map(_.nonEmpty).provide(layer)

}
