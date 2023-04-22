package muse.service.persist

import io.getquill.*
import io.getquill.context.ZioJdbc.*
import io.getquill.jdbczio.Quill
import muse.domain.common.EntityType
import muse.domain.common.Types.{RefreshToken, SessionId, UserId}
import muse.domain.mutate.*
import muse.domain.table.*
import zio.ZLayer.*
import zio.{Clock, IO, Schedule, TaskLayer, ZIO, ZLayer, durationInt}

import java.sql.{SQLException, Timestamp, Types}
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

trait DatabaseService {

  /**
   * Create!
   */
  def createUser(userId: UserId): IO[SQLException, Unit]

  def createUserSession(sessionId: SessionId, refreshToken: RefreshToken, userId: UserId): IO[SQLException, Unit]

  def createReview(userId: UserId, review: CreateReview): IO[SQLException, Review]

  def createReviewComment(userId: UserId, review: CreateComment)
      : IO[Throwable, (ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], List[ReviewCommentEntity])]

  def linkReviews(link: LinkReviews): IO[Throwable, Boolean]

  /**
   * Read!
   */
  def getUsers: IO[SQLException, List[User]]

  def getUserById(userId: UserId): IO[SQLException, Option[User]]

  def getUserSession(sessionId: SessionId): IO[SQLException, Option[UserSession]]

  // Reviews that the given user created.
  def getUserReviews(userId: UserId): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  // Reviews that the given user has access to.
  def getAllUserReviews(userId: UserId): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  // Reviews that viewerUser can see of sourceUser.
  def getUserReviewsExternal(sourceUserId: UserId, viewerUserId: UserId): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  def getReviewAndEntity(reviewId: UUID): IO[SQLException, Option[(Review, Option[ReviewEntity])]]

  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]

  def getReviewEntity(reviewId: UUID): IO[SQLException, Option[ReviewEntity]]

  // TODO: Might have to incorporate permissions here.
  // Review will only be returned if the user has access to it.
  def getReviewWithPermissions(reviewId: UUID, userId: UserId): IO[SQLException, Option[(Review, Option[ReviewEntity])]]

  // Multi review version of getReviewWithPermissions.
  def getReviewsWithPermissions(reviewIds: List[UUID], userId: UserId): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  // TODO: Might have to incorporate permissions here.
  def getChildReviews(reviewId: UUID): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  // Multi review version of getChildReviews.
  def getAllChildReviews(reviewIds: List[UUID]): IO[SQLException, List[(ReviewLink, Review, Option[ReviewEntity])]]

  def getReviewComments(reviewId: UUID)
      : IO[SQLException, List[(ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], Option[ReviewCommentEntity])]]

  def getComments(commentIds: List[Long], userId: UserId)
      : IO[SQLException, List[(ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], Option[ReviewCommentEntity])]]

  def getComment(commentId: Long, userId: UserId)
      : IO[SQLException, Option[(ReviewComment, ReviewCommentIndex, List[ReviewCommentParentChild], List[ReviewCommentEntity])]]

  def getAllCommentChildren(commentId: Long, userId: UserId)
      : IO[SQLException, List[(ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], Option[ReviewCommentEntity])]]

  def getCommentEntities(commentId: Long): IO[SQLException, List[ReviewCommentEntity]]

  /**
   * Update!
   */
  def updateReview(review: UpdateReview): IO[SQLException, Review]

  def updateReviewEntity(review: ReviewEntity): IO[SQLException, Boolean]

  def updateComment(comment: UpdateComment): IO[SQLException, ReviewComment]

  // commentId -> commentIndex
  def updateCommentIndex(commentId: Long, commentIndex: Int): IO[Throwable, List[(Long, Int)]]

  def shareReview(share: ShareReview): IO[SQLException, Boolean]

  def updateReviewLink(link: UpdateReviewLink): IO[Throwable, Boolean]

  /**
   * Delete!
   *
   * Returns whether a record was deleted.
   */
  def deleteReview(d: DeleteReview): IO[Throwable, Boolean]

  def deleteComment(d: DeleteComment): IO[Throwable, (List[Long], List[Long])]

  def deleteReviewLink(link: DeleteReviewLink): IO[Throwable, Boolean]

  def deleteUserSession(sessionId: String): IO[SQLException, Boolean]

  /**
   * Permissions!
   */
  def getAllUsersWithAccess(reviewIds: List[UUID]): IO[SQLException, List[ReviewAccess]]

  def canMakeComment(userId: UserId, reviewId: UUID): IO[SQLException, Boolean]

  def canModifyReview(userId: UserId, reviewId: UUID): IO[SQLException, Boolean]

  def canModifyComment(userId: UserId, reviewId: UUID, commentId: Long): IO[SQLException, Boolean]
}

object DatabaseService {
  val layer = ZLayer.fromFunction(DatabaseServiceLive.apply(_))

  def createUser(userId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.createUser(userId))

  def createUserSession(sessionId: SessionId, refreshToken: RefreshToken, userId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.createUserSession(sessionId, refreshToken, userId))

  def createOrUpdateUser(sessionId: SessionId, refreshToken: RefreshToken, userId: UserId) =
    createUser(userId)
      *> createUserSession(sessionId, refreshToken, userId)

  def createReview(userId: UserId, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.createReview(userId, review))

  def linkReviews(link: LinkReviews) =
    ZIO.serviceWithZIO[DatabaseService](_.linkReviews(link))

  def createReviewComment(userId: UserId, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.createReviewComment(userId, c))

  def getUsers = ZIO.serviceWithZIO[DatabaseService](_.getUsers)

  def getUserById(userId: UserId) = ZIO.serviceWithZIO[DatabaseService](_.getUserById(userId))

  def getUserSession(sessionId: SessionId) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserSession(sessionId))

  def getReview(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReview(reviewId))

  def getReviewEntity(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewEntity(reviewId))

  def getReviewWithPermissions(reviewId: UUID, userId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.getReviewWithPermissions(reviewId, userId))

  def getReviewsWithPermissions(reviewIds: List[UUID], userId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.getReviewsWithPermissions(reviewIds, userId))

  def getAllChildReviews(reviewIds: List[UUID]) = ZIO.serviceWithZIO[DatabaseService](_.getAllChildReviews(reviewIds))

  def getUserReviews(userId: UserId) = ZIO.serviceWithZIO[DatabaseService](_.getUserReviews(userId))

  def getAllUserReviews(userId: UserId) = ZIO.serviceWithZIO[DatabaseService](_.getAllUserReviews(userId))

  def getUserReviewsExternal(sourceUserId: UserId, viewerUserId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserReviewsExternal(sourceUserId, viewerUserId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewComments(reviewId))

  def getComment(id: Long, userId: UserId) = ZIO.serviceWithZIO[DatabaseService](_.getComment(id, userId))

  def getComments(ids: List[Long], userId: UserId) = ZIO.serviceWithZIO[DatabaseService](_.getComments(ids, userId))

  def getCommentEntities(id: Long) = ZIO.serviceWithZIO[DatabaseService](_.getCommentEntities(id))

  def getAllCommentChildren(commentId: Long, userId: UserId) =
    ZIO.serviceWithZIO[DatabaseService](_.getAllCommentChildren(commentId, userId))

  def getAllUsersWithAccess(reviewIds: List[UUID]) =
    ZIO.serviceWithZIO[DatabaseService](_.getAllUsersWithAccess(reviewIds))

  def updateReview(review: UpdateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReview(review))

  def updateReviewEntity(review: ReviewEntity) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReviewEntity(review))

  def updateComment(comment: UpdateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.updateComment(comment))

  def updateCommentIndex(commentId: Long, commentIndex: Int) =
    ZIO.serviceWithZIO[DatabaseService](_.updateCommentIndex(commentId, commentIndex))

  def updateReviewLink(link: UpdateReviewLink) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReviewLink(link))

  def shareReview(share: ShareReview) =
    ZIO.serviceWithZIO[DatabaseService](_.shareReview(share))

  def deleteReview(d: DeleteReview) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteReview(d))

  def deleteReviewLink(d: DeleteReviewLink) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteReviewLink(d))

  def deleteComment(d: DeleteComment) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteComment(d))

  def deleteUserSession(sessionId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.deleteUserSession(sessionId))

  def canModifyReview(userId: UserId, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyReview(userId, reviewId))

  def canModifyComment(userId: UserId, reviewId: UUID, commentId: Long) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyComment(userId, reviewId, commentId))

  def canMakeComment(userId: UserId, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canMakeComment(userId, reviewId))
}
