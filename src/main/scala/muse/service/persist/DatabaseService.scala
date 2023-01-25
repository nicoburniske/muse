package muse.service.persist

import io.getquill.*
import io.getquill.context.ZioJdbc.*
import io.getquill.jdbczio.Quill
import muse.domain.common.EntityType
import muse.domain.mutate.{
  CreateComment,
  CreateReview,
  DeleteComment,
  DeleteReview,
  DeleteReviewLink,
  LinkReviews,
  ReviewEntityInput,
  ShareReview,
  UpdateComment,
  UpdateReview,
  UpdateReviewLink
}
import muse.domain.table.{
  AccessLevel,
  Review,
  ReviewAccess,
  ReviewComment,
  ReviewCommentEntity,
  ReviewEntity,
  ReviewLink,
  User,
  UserSession
}
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
  def createReviewComment(id: String, review: CreateComment): IO[Throwable, (ReviewComment, List[ReviewCommentEntity])]
  def linkReviews(link: LinkReviews): IO[Throwable, Boolean]

  /**
   * Read!
   */
  def getUsers: IO[SQLException, List[User]]
  def getUserById(userId: String): IO[SQLException, Option[User]]
  def getUserSession(sessionId: String): IO[SQLException, Option[UserSession]]

  // Reviews that the given user created.
  def getUserReviews(userId: String): IO[SQLException, List[(Review, Option[ReviewEntity])]]
  // Reviews that the given user has access to.
  def getAllUserReviews(userId: String): IO[SQLException, List[(Review, Option[ReviewEntity])]]
  // Reviews that viewerUser can see of sourceUser.
  def getUserReviewsExternal(sourceUserId: String, viewerUserId: String): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  def getReviewComments(reviewId: UUID): IO[SQLException, List[(ReviewComment, Option[ReviewCommentEntity])]]
  def getMultiReviewComments(reviewIds: List[UUID]): IO[SQLException, List[(ReviewComment, Option[ReviewCommentEntity])]]
  def getReviews(reviewIds: List[UUID]): IO[SQLException, List[Review]]
  def getReviewAndEntity(reviewId: UUID): IO[SQLException, Option[(Review, Option[ReviewEntity])]]
  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]
  def getReviewEntity(reviewId: UUID): IO[SQLException, Option[ReviewEntity]]
  def getReviewWithPermissions(reviewId: UUID, userId: String): IO[SQLException, Option[(Review, Option[ReviewEntity])]]
  def getChildReviews(reviewId: UUID): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  def getUsersWithAccess(reviewId: UUID): IO[SQLException, List[ReviewAccess]]
  def getComment(commentId: Int): IO[SQLException, Option[(ReviewComment, List[ReviewCommentEntity])]]

  /**
   * Update!
   */
  def updateReview(review: UpdateReview): IO[SQLException, Review]
  def updateReviewEntity(review: ReviewEntity): IO[SQLException, Boolean]
  def updateComment(comment: UpdateComment): IO[SQLException, ReviewComment]
  def shareReview(share: ShareReview): IO[SQLException, Boolean]
  def updateReviewLink(link: UpdateReviewLink): IO[Throwable, Boolean]

  /**
   * Delete!
   *
   * Returns whether a record was deleted.
   */
  def deleteReview(d: DeleteReview): IO[Throwable, Boolean]
  // TODO: fix delete to mark boolean field as deleted.
  def deleteComment(d: DeleteComment): IO[SQLException, Boolean]
  def deleteReviewLink(link: DeleteReviewLink): IO[Throwable, Boolean]
  def deleteUserSession(sessionId: String): IO[SQLException, Boolean]

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

  def linkReviews(link: LinkReviews) =
    ZIO.serviceWithZIO[DatabaseService](_.linkReviews(link))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserById(userId))

  def getUserSession(sessionId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserSession(sessionId))

  def getReviewAndEntity(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewAndEntity(reviewId))
  def getReview(reviewId: UUID)          = ZIO.serviceWithZIO[DatabaseService](_.getReview(reviewId))
  def getReviewEntity(reviewId: UUID)    = ZIO.serviceWithZIO[DatabaseService](_.getReviewEntity(reviewId))

  def getChildReviews(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getChildReviews(reviewId))

  def getUsers = ZIO.serviceWithZIO[DatabaseService](_.getUsers)

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getAllUserReviews(userId))

  def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserReviewsExternal(sourceUserId, viewerUserId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewComments(reviewId))

  def getComment(id: Int) = ZIO.serviceWithZIO[DatabaseService](_.getComment(id))

  def getAllReviewComments(reviewIds: List[UUID]) = ???

  def getUsersWithAccess(reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.getUsersWithAccess(reviewId))

  def updateReview(review: UpdateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReview(review))

  def updateReviewEntity(review: ReviewEntity) =
    ZIO.serviceWithZIO[DatabaseService](_.updateReviewEntity(review))

  def updateComment(comment: UpdateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.updateComment(comment))

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
}

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  // Exponential backoff retry strategy for connecting to Postgres DB.
  val schedule        = Schedule.exponential(1.second) && Schedule.recurs(10)
  val dataSourceLayer = Quill.DataSource.fromPrefix("database").retry(schedule)

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

  inline def review = querySchema[Review]("muse.review")

  inline def reviewEntity = querySchema[ReviewEntity]("muse.review_entity")

  inline def reviewLink = querySchema[ReviewLink]("muse.review_link")

  inline def reviewAccess = querySchema[ReviewAccess]("muse.review_access")

  inline def comment = querySchema[ReviewComment]("muse.review_comment")

  inline def commentEntity = querySchema[ReviewCommentEntity]("muse.review_comment_entity")

  /**
   * Read!
   */

  inline def userReviews(inline userId: String) = review.filter(_.creatorId == userId)

  inline def userSharedReviews(inline userId: String) =
    reviewAccess
      .filter(_.userId == userId)
      .join(review)
      .on((access, review) => review.id == access.reviewId)
      .map(_._2)

  inline def allUserReviews(inline userId: String) =
    userReviews(userId) union userSharedReviews(userId)

  override def getAllUserReviews(userId: String) = run {
    allUserReviews(lift(userId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.id == entity.reviewId)
  }.provide(layer)

  override def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) = run {
    for {
      review <- userReviews(lift(sourceUserId)).filter(_.isPublic) union userSharedReviews(lift(viewerUserId))
      if review.creatorId == lift(sourceUserId)
      entity <- reviewEntity.leftJoin(_.reviewId == review.id)
    } yield (review, entity)
  }.provide(layer)

  override def getUsers = run(user).provide(layer)

  override def getReviewAndEntity(reviewId: UUID) = run {
    review
      .filter(_.id == lift(reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.id == entity.reviewId)
  }.provide(layer)
    .map(_.headOption)

  override def getReview(reviewId: UUID) = run {
    review.filter(_.id == lift(reviewId))
  }.provide(layer)
    .map(_.headOption)

  override def getReviewEntity(reviewId: UUID) = run {
    reviewEntity.filter(_.reviewId == lift(reviewId))
  }.provide(layer)
    .map(_.headOption)

  override def getReviewWithPermissions(reviewId: UUID, userId: String) = run {
    allViewableReviews(lift(userId))
      .filter(_.id == lift(reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.id == entity.reviewId)
  }.provide(layer)
    .map(_.headOption)

  override def getChildReviews(reviewId: UUID) = run {
    (for {
      link           <- reviewLink.filter(_.parentReviewId == lift(reviewId))
      child          <- review.join(_.id == link.childReviewId)
      reviewEntities <- reviewEntity.leftJoin(e => child.id == e.reviewId)
    } yield (link, child, reviewEntities))
      .sortBy(_._1.linkIndex)
      .map { case (_, review, entity) => (review, entity) }
  }.provide(layer)

  override def getUserById(userId: String) = run {
    user.filter(_.id == lift(userId))
  }.map(_.headOption).provide(layer)

  override def getUserSession(sessionId: String) = run {
    userSession.filter(_.sessionId == lift(sessionId))
  }.map(_.headOption).provide(layer)

  override def getUserReviews(userId: String) = run {
    userReviews(lift(userId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.id == entity.reviewId)
  }.provide(layer)

  override def getReviewComments(reviewId: UUID) = run {
    comment
      .filter(_.reviewId == lift(reviewId))
      .leftJoin(commentEntity)
      .on((comment, entity) => comment.id == entity.commentId)
  }.provide(layer)

  override def getMultiReviewComments(reviewIds: List[UUID]) = run {
    comment
      .filter(c => liftQuery(reviewIds.toSet).contains(c.reviewId))
      .leftJoin(commentEntity)
      .on((comment, entity) => comment.id == entity.commentId)
  }.provideLayer(layer)

  override def getReviews(reviewIds: List[UUID]) = run {
    review.filter(c => liftQuery(reviewIds.toSet).contains(c.id))
  }.provideLayer(layer)

  override def getUsersWithAccess(reviewId: UUID) = run {
    reviewAccess.filter(_.reviewId == lift(reviewId))
  }.provideLayer(layer)

  override def getComment(commentId: Int) = run {
    comment
      .filter(_.id == lift(commentId))
      .leftJoin(commentEntity)
      .on((comment, entity) => comment.id == entity.commentId)
  }.provideLayer(layer)
    .map { found => found.headOption.map(_._1 -> found.flatMap(_._2)) }

  /**
   * Create!
   */

  override def createUser(userId: String) = run {
    user
      .insert(
        _.id -> lift(userId)
      ).onConflictIgnore
  }.provideLayer(layer).unit

  override def createUserSession(sessionId: String, refreshToken: String, userId: String) = run {
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
        _.isPublic   -> lift(create.isPublic)
      )
      .returningGenerated(r => r.id -> r.createdAt)
  }.provide(layer).map {
    case (uuid, instant) =>
      Review(uuid, instant, userId, create.name, create.isPublic)
  }

  override def createReviewComment(userId: String, c: CreateComment) = transaction {
    for {
      created                   <- run {
                                     comment
                                       .insert(
                                         _.reviewId        -> lift(c.reviewId),
                                         _.commenter       -> lift(userId),
                                         _.parentCommentId -> lift(c.parentCommentId),
                                         // No idea why I can't use 'Some' here.
                                         _.comment         -> lift(Option.apply(c.comment))
                                       )
                                       .returningGenerated(gen => (gen.id, gen.createdAt, gen.updatedAt))
                                   }
      (id, createdAt, updatedAt) = created
      _                         <- run {
                                     liftQuery(c.entities)
                                       .foreach(entity =>
                                         commentEntity.insert(
                                           _.commentId  -> lift(id),
                                           _.entityType -> entity.entityType,
                                           _.entityId   -> entity.entityId
                                         ))
                                   }
    } yield {
      val createdComment  =
        ReviewComment(id, createdAt, updatedAt, false, c.parentCommentId, c.reviewId, userId, Some(c.comment))
      val commentEntities = c.entities.map(input => ReviewCommentEntity(id, input.entityType, input.entityId))
      createdComment -> commentEntities
    }
  }.provide(layer)

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

  override def updateReviewEntity(review: ReviewEntity) = run {
    reviewEntity
      .insert(
        _.reviewId   -> lift(review.reviewId),
        _.entityType -> lift(review.entityType),
        _.entityId   -> lift(review.entityId)
      ).onConflictUpdate(_.reviewId)(
        (t, e) => t.entityId -> e.entityId,
        (t, e) => t.entityType -> e.entityType
      )
  }.provide(layer)
    .map(_ > 0)

  override def updateComment(c: UpdateComment) = ZIO
    .succeed(Instant.now()).flatMap { now =>
      run {
        comment
          .filter(_.id == lift(c.commentId))
          .filter(_.reviewId == lift(c.reviewId))
          .update(
            _.comment   -> lift(c.comment),
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

  inline def getReviewLink(inline parentReviewId: UUID, inline childReviewId: UUID) =
    reviewLink
      .filter(_.parentReviewId == lift(parentReviewId))
      .filter(_.childReviewId == lift(childReviewId))

  override def updateReviewLink(update: UpdateReviewLink) = transaction {
    for {
      currentLinkIndex <- run {
                            getReviewLink(update.parentReviewId, update.childReviewId)
                              .map(_.linkIndex)
                              .take(1)
                          }.map(_.headOption)
      // Move entries above current placement down.
      _                <- run {
                            reviewLink
                              .filter(_.parentReviewId == lift(update.parentReviewId))
                              .filter(link => lift(currentLinkIndex).exists(link.linkIndex >= _))
                              .update(link => link.linkIndex -> (link.linkIndex - 1))
                          }
      // Move entries below new placement up.
      _                <- run {
                            reviewLink
                              .filter(_.parentReviewId == lift(update.parentReviewId))
                              .filter(_.linkIndex >= lift(update.linkIndex))
                              .update(link => link.linkIndex -> (link.linkIndex + 1))
                          }
      // Update index.
      update           <- run {
                            getReviewLink(update.parentReviewId, update.childReviewId)
                              .update(_.linkIndex -> lift(update.linkIndex))
                          }
    } yield update
  }.provide(layer)
    .map(_ > 0)

  override def linkReviews(link: LinkReviews) = (link.linkIndex match
    // Find the current greatest index and then insert the new link at that index + 1.
    case None            =>
      transaction {
        for {
          insertIndex <- run {
                           reviewLink
                             .filter(_.parentReviewId == lift(link.parentReviewId))
                             .size
                         }
          insertCount <- run {
                           reviewLink
                             .insert(
                               _.parentReviewId -> lift(link.parentReviewId),
                               _.childReviewId  -> lift(link.childReviewId),
                               // TODO: Is Long -> Int safe?
                               _.linkIndex      -> lift(insertIndex.toInt)
                             )
                         }
        } yield insertCount
      }
    case Some(linkIndex) =>
      transaction {
        for {
          _           <- run {
                           reviewLink
                             .filter(_.parentReviewId == lift(link.parentReviewId))
                             .filter(_.linkIndex >= lift(linkIndex))
                             .update(link => link.linkIndex -> (link.linkIndex + 1))
                         }
          insertCount <- run {
                           reviewLink
                             .insert(
                               _.parentReviewId -> lift(link.parentReviewId),
                               _.childReviewId  -> lift(link.childReviewId),
                               _.linkIndex      -> lift(linkIndex)
                             )
                         }
        } yield insertCount
      }
  ).provide(layer).map(_ > 0)

  /**
   * Delete!
   */
  override def deleteReview(delete: DeleteReview) = transaction {
    for {
      // We want to delete all links.
      deletedLinks <- run {
                        reviewLink
                          .filter(_.childReviewId == lift(delete.id))
                          .delete
                          .returningMany(deleted => deleted)
                      }
      // Ensure there are no holes.
      _            <- run {
                        liftQuery(deletedLinks).foreach { deletedLink =>
                          reviewLink
                            .filter(_.parentReviewId == deletedLink.parentReviewId)
                            .filter(_.linkIndex >= deletedLink.linkIndex)
                            .update(link => link.linkIndex -> (link.linkIndex - 1))
                        }
                      }
      // Then delete the review.
      deleted      <- run {
                        review.filter(_.id == lift(delete.id)).delete
                      }
    } yield deleted
  }
    .provide(layer)
    .map(_ > 0)

  override def deleteComment(d: DeleteComment) = run {
    comment
      .filter(_.id == lift(d.commentId))
      .filter(_.reviewId == lift(d.reviewId))
      .delete
  }.provide(layer)
    .map(_ > 0)

  override def deleteReviewLink(link: DeleteReviewLink) = transaction {
    for {
      deleteIndex <- run {
                       reviewLink
                         .filter(_.parentReviewId == lift(link.parentReviewId))
                         .filter(_.childReviewId == lift(link.childReviewId))
                         .delete
                         .returning(deleted => deleted.linkIndex)
                     }
      _           <- run {
                       reviewLink
                         .filter(_.parentReviewId == lift(link.parentReviewId))
                         .filter(_.linkIndex >= lift(deleteIndex))
                         .update(link => link.linkIndex -> (link.linkIndex - 1))
                     }
    } yield deleteIndex
  }.provide(layer)
    .map(_ > 0)

  def deleteUserSession(sessionId: String) = run {
    userSession
      .filter(_.sessionId == lift(sessionId))
      .delete
  }.provide(layer)
    .map(_ > 0)

  /**
   * Permissions logic.
   */

  inline def reviewCreator(inline reviewId: UUID): EntityQuery[String] =
    review.filter(_.id == reviewId).map(_.creatorId)

  inline def usersWithWriteAccess(inline reviewId: UUID): EntityQuery[String] =
    reviewAccess
      .filter(_.reviewId == reviewId)
      .filter(_.accessLevel == lift(AccessLevel.Collaborator))
      .map(_.userId)

  inline def allUsersWithWriteAccess(inline reviewId: UUID) =
    reviewCreator(reviewId) union usersWithWriteAccess(reviewId)

  // Is review public?
  // Or does user have access to it?
  inline def allViewableReviews(inline userId: String) =
    review.filter(_.isPublic) union allUserReviews(userId)

  // TODO: test this!
  override def canViewReview(userId: String, reviewId: UUID) =
    run {
      allViewableReviews(lift(userId))
        .filter(_.id == lift(reviewId))
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
