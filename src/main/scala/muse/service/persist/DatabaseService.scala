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
  UpdateCommentIndex,
  UpdateReview,
  UpdateReviewLink
}
import muse.domain.table.{
  AccessLevel,
  Review,
  ReviewAccess,
  ReviewComment,
  ReviewCommentEntity,
  ReviewCommentIndex,
  ReviewCommentParentChild,
  ReviewEntity,
  ReviewLink,
  User,
  UserSession
}
import zio.ZLayer.*
import zio.{Clock, IO, Schedule, TaskLayer, ZIO, ZLayer, durationInt}

import java.sql.{SQLException, Timestamp, Types}
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

type FullComment = (ReviewComment, ReviewCommentIndex, List[ReviewCommentEntity], Option[ReviewCommentParentChild])

trait DatabaseService {

  /**
   * Create!
   */
  def createUser(userId: String): IO[SQLException, Unit]
  def createUserSession(sessionId: String, refreshToken: String, userId: String): IO[SQLException, Unit]
  def createReview(id: String, review: CreateReview): IO[SQLException, Review]
  def createReviewComment(id: String, review: CreateComment)
      : IO[Throwable, (ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], List[ReviewCommentEntity])]
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

  def getReviewAndEntity(reviewId: UUID): IO[SQLException, Option[(Review, Option[ReviewEntity])]]
  def getReview(reviewId: UUID): IO[SQLException, Option[Review]]
  def getReviewEntity(reviewId: UUID): IO[SQLException, Option[ReviewEntity]]

  // TODO: Might have to incorporate permissions here.
  // Review will only be returned if the user has access to it.
  def getReviewWithPermissions(reviewId: UUID, userId: String): IO[SQLException, Option[(Review, Option[ReviewEntity])]]
  // Multi review version of getReviewWithPermissions.
  def getReviewsWithPermissions(reviewIds: List[UUID], userId: String): IO[SQLException, List[(Review, Option[ReviewEntity])]]

  // TODO: Might have to incorporate permissions here.
  def getChildReviews(reviewId: UUID): IO[SQLException, List[(Review, Option[ReviewEntity])]]
  // Multi review version of getChildReviews.
  def getAllChildReviews(reviewIds: List[UUID]): IO[SQLException, List[(ReviewLink, Review, Option[ReviewEntity])]]

  def getReviewComments(reviewId: UUID)
      : IO[SQLException, List[(ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], Option[ReviewCommentEntity])]]
  def getComments(commentIds: List[Long])
      : IO[SQLException, List[(ReviewComment, ReviewCommentIndex, Option[ReviewCommentParentChild], Option[ReviewCommentEntity])]]
  def getComment(commentId: Long)
      : IO[SQLException, Option[(ReviewComment, ReviewCommentIndex, List[ReviewCommentParentChild], List[ReviewCommentEntity])]]
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
  def canMakeComment(userId: String, reviewId: UUID): IO[SQLException, Boolean]
  def canModifyReview(userId: String, reviewId: UUID): IO[SQLException, Boolean]
  def canModifyComment(userId: String, reviewId: UUID, commentId: Long): IO[SQLException, Boolean]

}

object DatabaseService {
  val layer = ZLayer.fromFunction(DatabaseServiceLive.apply(_))

  def createUser(userId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.createUser(userId))

  def createUserSession(sessionId: String, refreshToken: String, userId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.createUserSession(sessionId, refreshToken, userId))

  def createOrUpdateUser(sessionId: String, refreshToken: String, userId: String) =
    createUser(userId)
      *> createUserSession(sessionId, refreshToken, userId)

  def createReview(userId: String, review: CreateReview) =
    ZIO.serviceWithZIO[DatabaseService](_.createReview(userId, review))

  def linkReviews(link: LinkReviews) =
    ZIO.serviceWithZIO[DatabaseService](_.linkReviews(link))

  def createReviewComment(userId: String, c: CreateComment) =
    ZIO.serviceWithZIO[DatabaseService](_.createReviewComment(userId, c))

  def getUserById(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserById(userId))

  def getUserSession(sessionId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserSession(sessionId))

  def getReview(reviewId: UUID)       = ZIO.serviceWithZIO[DatabaseService](_.getReview(reviewId))
  def getReviewEntity(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewEntity(reviewId))

  def getReviewWithPermissions(reviewId: UUID, userId: String)         =
    ZIO.serviceWithZIO[DatabaseService](_.getReviewWithPermissions(reviewId, userId))
  def getReviewsWithPermissions(reviewIds: List[UUID], userId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getReviewsWithPermissions(reviewIds, userId))

  def getAllChildReviews(reviewIds: List[UUID]) = ZIO.serviceWithZIO[DatabaseService](_.getAllChildReviews(reviewIds))

  def getUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getUserReviews(userId))

  def getAllUserReviews(userId: String) = ZIO.serviceWithZIO[DatabaseService](_.getAllUserReviews(userId))

  def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) =
    ZIO.serviceWithZIO[DatabaseService](_.getUserReviewsExternal(sourceUserId, viewerUserId))

  def getReviewComments(reviewId: UUID) = ZIO.serviceWithZIO[DatabaseService](_.getReviewComments(reviewId))

  def getComment(id: Long)         = ZIO.serviceWithZIO[DatabaseService](_.getComment(id))
  def getComments(ids: List[Long]) = ZIO.serviceWithZIO[DatabaseService](_.getComments(ids))
  def getCommentEntities(id: Long) = ZIO.serviceWithZIO[DatabaseService](_.getCommentEntities(id))

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

  def canModifyReview(userId: String, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyReview(userId, reviewId))

  def canModifyComment(userId: String, reviewId: UUID, commentId: Long) =
    ZIO.serviceWithZIO[DatabaseService](_.canModifyComment(userId, reviewId, commentId))

  def canMakeComment(userId: String, reviewId: UUID) =
    ZIO.serviceWithZIO[DatabaseService](_.canMakeComment(userId, reviewId))
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

  inline def commentIndex = querySchema[ReviewCommentIndex]("muse.review_comment_index")

  inline def commentParentChild = querySchema[ReviewCommentParentChild]("muse.review_comment_parent_child")

  /**
   * Read!
   */

  inline def userReviews(inline userId: String) = review.filter(_.creatorId == userId)

  inline def userSharedReviews(inline userId: String) =
    reviewAccess
      .filter(_.userId == userId)
      .join(review)
      .on((access, review) => review.reviewId == access.reviewId)
      .map(_._2)

  inline def allUserReviews(inline userId: String) =
    userReviews(userId) union userSharedReviews(userId)

  override def getAllUserReviews(userId: String) = run {
    allUserReviews(lift(userId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)

  override def getUserReviewsExternal(sourceUserId: String, viewerUserId: String) = run {
    for {
      review <- userReviews(lift(sourceUserId)).filter(_.isPublic) union userSharedReviews(lift(viewerUserId))
      if review.creatorId == lift(sourceUserId)
      entity <- reviewEntity.leftJoin(_.reviewId == review.reviewId)
    } yield (review, entity)
  }.provide(layer)

  override def getUsers = run(user).provide(layer)

  override def getReviewAndEntity(reviewId: UUID) = run {
    review
      .filter(_.reviewId == lift(reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)
    .map(_.headOption)

  override def getReview(reviewId: UUID) = run {
    review.filter(_.reviewId == lift(reviewId))
  }.provide(layer)
    .map(_.headOption)

  override def getReviewEntity(reviewId: UUID) = run {
    reviewEntity.filter(_.reviewId == lift(reviewId))
  }.provide(layer)
    .map(_.headOption)

  override def getReviewWithPermissions(reviewId: UUID, userId: String) = run {
    allViewableReviews(lift(userId))
      .filter(_.reviewId == lift(reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)
    .map(_.headOption)

  override def getReviewsWithPermissions(reviewIds: List[UUID], userId: String) = run {
    allViewableReviews(lift(userId))
      .filter(r => liftQuery(reviewIds.toSet).contains(r.reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)

  override def getChildReviews(reviewId: UUID) = run {
    (for {
      link           <- reviewLink.filter(_.parentReviewId == lift(reviewId))
      child          <- review.join(_.reviewId == link.childReviewId)
      reviewEntities <- reviewEntity.leftJoin(e => child.reviewId == e.reviewId)
    } yield (link, child, reviewEntities))
      .sortBy(_._1.linkIndex)
      .map { case (_, review, entity) => (review, entity) }
  }.provide(layer)

  override def getAllChildReviews(reviewIds: List[UUID]) = run {
    (for {
      link           <- reviewLink.filter(link => liftQuery(reviewIds.toSet).contains(link.parentReviewId))
      child          <- review.join(_.reviewId == link.childReviewId)
      reviewEntities <- reviewEntity.leftJoin(e => child.reviewId == e.reviewId)
    } yield (link, child, reviewEntities))
  }.provide(layer)

  override def getUserById(userId: String) = run {
    user.filter(_.userId == lift(userId))
  }.map(_.headOption).provide(layer)

  override def getUserSession(sessionId: String) = run {
    userSession.filter(_.sessionId == lift(sessionId))
  }.map(_.headOption).provide(layer)

  override def getUserReviews(userId: String) = run {
    userReviews(lift(userId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)

  override def getComment(commentId: Long) = run {
    for {
      comment       <- comment.filter(_.commentId == lift(commentId))
      commentIndex  <- commentIndex.join(_.commentId == comment.commentId)
      parentComment <- commentParentChild.leftJoin(_.childCommentId == comment.commentId)
      entity        <- commentEntity.leftJoin(_.commentId == comment.commentId)
    } yield (comment, commentIndex, parentComment, entity)
  }.provide(layer)
    .map { rows =>
      rows.headOption.map {
        case (comment, commentIndex, _, _) =>
          val parentChild = rows.flatMap(_._3)
          val entities    = rows.flatMap(_._4)
          (comment, commentIndex, parentChild, entities)
      }
    }

  override def getReviewComments(reviewId: UUID) = run {
    val comments = comment.filter(_.reviewId == lift(reviewId))
    getAllCommentData(comments)
  }.provide(layer)

  override def getComments(commentIds: List[Long]) = run {
    val comments = comment.filter(c => liftQuery(commentIds.toSet).contains(c.commentId))
    getAllCommentData(comments)
  }.provide(layer)

  private inline def getAllCommentData(query: Query[ReviewComment]) = for {
    comment       <- query
    // There should always be an index!
    commentIndex  <- commentIndex.join(_.commentId == comment.commentId)
    childComments <- commentParentChild.leftJoin { parentChild =>
                       parentChild.parentCommentId == comment.commentId ||
                       parentChild.childCommentId == comment.commentId
                     }
    entity        <- commentEntity.leftJoin(_.commentId == comment.commentId)
  } yield (comment, commentIndex, childComments, entity)

  def getAllUsersWithAccess(reviewIds: List[UUID]) = run {
    reviewAccess.filter { c =>
      liftQuery(reviewIds.toSet)
        .contains(c.reviewId)
    }
  }.provideLayer(layer)

  override def getCommentEntities(commentId: Long) = run {
    commentEntity
      .filter(entity => entity.commentId == lift(commentId))
  }.provideLayer(layer)

  /**
   * Create!
   */

  override def createUser(userId: String) = run {
    user
      .insert(
        _.userId -> lift(userId)
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
      .returningGenerated(r => r.reviewId -> r.createdAt)
  }.provide(layer).map {
    case (uuid, instant) =>
      Review(uuid, instant, userId, create.name, create.isPublic)
  }

  override def createReviewComment(userId: String, create: CreateComment) = {
    def insertComment(insertIndex: Int) = transaction {
      for {
        createdComment     <- run {
                                comment
                                  .insert(
                                    _.reviewId  -> lift(create.reviewId),
                                    _.commenter -> lift(userId),
                                    // No idea why I can't use 'Some' here.
                                    _.comment   -> lift(Option.apply(create.comment))
                                  ).returningGenerated(comment =>
                                    (comment.commentId, comment.createdAt, comment.updatedAt, comment.deleted))
                              }
        createdIndex       <- run {
                                commentIndex
                                  .insert(
                                    _.reviewId        -> lift(create.reviewId),
                                    _.commentId       -> lift(createdComment._1),
                                    _.commentIndex    -> lift(insertIndex),
                                    _.parentCommentId -> lift(create.parentCommentId)
                                  )
                                  .returning(r => r)
                              }
        createdParentChild <- create.parentCommentId match {
                                case None                  => ZIO.succeed(None)
                                case Some(parentCommentId) =>
                                  run {
                                    commentParentChild
                                      .insert(
                                        _.parentCommentId -> lift(parentCommentId),
                                        _.childCommentId  -> lift(createdComment._1)
                                      )
                                      .returning(r => r)
                                  }.map(Some(_))
                              }
        createdEntities    <- run {
                                liftQuery(create.entities)
                                  .foreach { entity =>
                                    commentEntity
                                      .insert(
                                        _.commentId  -> lift(createdComment._1),
                                        _.entityType -> entity.entityType,
                                        _.entityId   -> entity.entityId
                                      ).returning(r => r)
                                  }
                              }
      } yield (createdComment, createdIndex, createdParentChild, createdEntities)
    }.map {
      case ((id, createdAt, updatedAt, deleted), createdIndex, createdParentChild, createdEntities) =>
        (
          ReviewComment(id, createdAt, updatedAt, deleted, create.reviewId, userId, Some(create.comment)),
          createdIndex,
          createdParentChild,
          createdEntities
        )
    }

    // INDEX IS ONLY UNIQUE PER PARENT COMMENT.
    create.commentIndex match {
      // Insert the comment at the end of the list.
      case None              =>
        for {
          _          <- ZIO.logInfo(s"Searching for index ${create.parentCommentId.getClass.toString}")
          topIndex   <- run {
                          commentIndex
                            .filter(_.reviewId == lift(create.reviewId))
                            .filter(_.parentCommentId == lift(create.parentCommentId))
                            .map(_.commentIndex)
                            .max
                        }
          insertIndex = topIndex.map(_ + 1).getOrElse(0)
          _          <- ZIO.logInfo(s"Inserting comment at index $insertIndex")
          created    <- insertComment(insertIndex)
        } yield created
      // Move all other comments down. Then insert the new comment at the specified index.
      case Some(insertIndex) =>
        transaction {
          for {
            _       <- run {
                         commentIndex
                           .filter(_.reviewId == lift(create.reviewId))
                           .filter(_.parentCommentId == lift(create.parentCommentId))
                           .filter(_.commentIndex >= lift(insertIndex))
                           .update(comment => comment.commentIndex -> (comment.commentIndex + 1))
                       }
            created <- insertComment(insertIndex)
          } yield created
        }
    }
  }.provide(layer)

  /**
   * Update!
   */
  override def updateReview(r: UpdateReview) = run {
    review
      .filter(_.reviewId == lift(r.reviewId))
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

  override def updateComment(c: UpdateComment) = Clock
    .instant.flatMap { now =>
      run {
        comment
          .filter(_.commentId == lift(c.commentId))
          .filter(_.reviewId == lift(c.reviewId))
          .update(
            _.comment   -> lift(Option.apply(c.comment)),
            _.updatedAt -> lift(now)
          ).returning(r => r)
      }
    }.provide(layer)

  override def updateCommentIndex(commentId: Long, newCommentIndex: Int) = Clock
    .instant.flatMap { now =>
      transaction {
        for {
          current <- run {
                       commentIndex
                         .filter(_.commentId == lift(commentId))
                     }.map(_.headOption) <&> run {
                       commentParentChild
                         .filter(_.childCommentId == lift(commentId))
                         .map(_.parentCommentId)
                     }.map(_.headOption)

          (currentIndex, parentCommentId: Option[Long]) = current
          currentCommentIndex: Option[Int]              = currentIndex.map(_.commentIndex)
          reviewId: Option[UUID]                        = currentIndex.map(_.reviewId)

          // Move entries above current placement down.
          movedDownComments <- run {
                                 commentIndex
                                   .filter(comment => lift(reviewId).contains(comment.reviewId))
                                   .filter(_.parentCommentId == lift(parentCommentId))
                                   .filter(comment => lift(currentCommentIndex).exists(comment.commentIndex > _))
                                   .update(comment => comment.commentIndex -> (comment.commentIndex - 1))
                                   .returningMany(c => c.commentId -> c.commentIndex)
                               }
          // Move entries below new placement up.
          movedUpComments   <- run {
                                 commentIndex
                                   .filter(comment => lift(reviewId).contains(comment.reviewId))
                                   .filter(_.parentCommentId == lift(parentCommentId))
                                   .filter(_.commentIndex >= lift(newCommentIndex))
                                   .update(c => c.commentIndex -> (c.commentIndex + 1))
                                   .returningMany(c => c.commentId -> c.commentIndex)
                               }
          // Update index.
          updatedComment    <- run {
                                 commentIndex
                                   .filter(_.commentId == lift(commentId))
                                   .update(
                                     _.commentIndex -> lift(newCommentIndex)
                                   )
                                   .returning(c => c.commentId -> c.commentIndex)
                               }
        } yield updatedComment :: (movedDownComments ++ movedUpComments)
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
                          }.map(_.headOption)
      // Move entries above current placement down.
      _                <- run {
                            reviewLink
                              .filter(_.parentReviewId == lift(update.parentReviewId))
                              .filter(link => lift(currentLinkIndex).exists(link.linkIndex > _))
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
    case None              =>
      for {
        topIndex    <- run {
                         reviewLink
                           .filter(_.parentReviewId == lift(link.parentReviewId))
                           .map(_.linkIndex)
                           .max
                       }
        insertIndex  = topIndex.map(_ + 1).getOrElse(0)
        insertCount <- run(insertLink(link.parentReviewId, link.childReviewId, insertIndex))
      } yield insertCount
    // Move all other links down. Then insert the new link at the specified index.
    case Some(insertIndex) =>
      transaction {
        for {
          _           <- run {
                           reviewLink
                             .filter(_.parentReviewId == lift(link.parentReviewId))
                             .filter(_.linkIndex >= lift(insertIndex))
                             .update(link => link.linkIndex -> (link.linkIndex + 1))
                         }
          insertCount <- run(insertLink(link.parentReviewId, link.childReviewId, insertIndex))
        } yield insertCount
      }
  ).provide(layer).map(_ > 0)

  inline def insertLink(inline parentReviewId: UUID, inline childReviewId: UUID, inline linkIndex: Int) =
    reviewLink
      .insert(
        _.parentReviewId -> lift(parentReviewId),
        _.childReviewId  -> lift(childReviewId),
        _.linkIndex      -> lift(linkIndex)
      )

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
                        review.filter(_.reviewId == lift(delete.id)).delete
                      }
    } yield deleted
  }
    .provide(layer)
    .map(_ > 0)

  override def deleteComment(d: DeleteComment) = run {
    commentParentChild
      .filter(_.parentCommentId == lift(d.commentId))
      .size
  }.map(_ > 0).flatMap {
      // If the comment is a parent to another comment, then mark the comment as deleted.
      case true  =>
        run {
          comment
            .filter(_.commentId == lift(d.commentId))
            .filter(_.reviewId == lift(d.reviewId))
            .update(
              _.deleted -> true,
              _.comment -> None
            )
        }.map {
          case 0 => Nil -> Nil
          case _ => Nil -> List(d.commentId)
        }
      // Otherwise full delete the comment.
      case false =>
        transaction {
          for {
            parentCommentId: Option[Long] <- run {
                                               commentParentChild
                                                 .filter(_.childCommentId == lift(d.commentId))
                                                 .map(_.parentCommentId)
                                             }.map(_.headOption)

            // Save comment index.
            deletedIndex                  <- run {
                                               commentIndex
                                                 .filter(_.commentId == lift(d.commentId))
                                                 .filter(_.reviewId == lift(d.reviewId))
                                                 .map(_.commentIndex)
                                             }.map(_.headOption)
            // Delete comment. This will cascade delete dependent rows in commentParentChild + commentIndex.
            deletedCommentId              <- run {
                                               comment
                                                 .filter(_.commentId == lift(d.commentId))
                                                 .delete
                                                 .returning(_.commentId)
                                             }
            // Move all comments (with same parent  &&  above the deleted comment) down.
            movedDownIds                  <- run {
                                               commentIndex
                                                 .filter(_.parentCommentId == lift(parentCommentId))
                                                 .filter(index => lift(deletedIndex).exists(_ <= index.commentIndex))
                                                 .update(comment => comment.commentIndex -> (comment.commentIndex - 1))
                                                 .returningMany(_.commentId)
                                             }

            // Find parent that is deleted and is not referenced by any other comments.
            maybeParentIndexToDelete      <- run {
                                               for {
                                                 parentIndex   <- commentIndex.filter { index =>
                                                                    lift(parentCommentId).contains(index.commentId)
                                                                  }
                                                 parentComment <- comment.join(_.commentId == parentIndex.commentId)
                                                 if parentComment.deleted
                                                 if commentParentChild.filter(_.parentCommentId == parentComment.commentId).size == 0
                                               } yield parentIndex
                                             }.map(_.headOption)
            maybeParentIdToDelete          = maybeParentIndexToDelete.map(_.commentId)

            // Delete dangling parent.
            _ <- run {
                   comment
                     .filter(c => lift(maybeParentIdToDelete).contains(c.commentId))
                     .delete
                 }

            maybeGrandparentId           = maybeParentIndexToDelete.flatMap(_.parentCommentId)
            maybeParentIndex             = maybeParentIndexToDelete.map(_.commentIndex)
            // Move all comments on parent level down if it was deleted.
            adjustedParents: List[Long] <- run {
                                             commentIndex
                                               .filter(index => lift(maybeGrandparentId) == index.parentCommentId)
                                               .filter(index => lift(maybeParentIndex).exists(_ <= index.commentIndex))
                                               .update(index => index.commentIndex -> (index.commentIndex - 1))
                                               .returningMany(_.commentId)
                                           }
          } yield (deletedCommentId :: maybeParentIdToDelete.fold(Nil)(List(_))) -> (movedDownIds ++ adjustedParents)
        }
    }.provide(layer)

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
    review.filter(_.reviewId == reviewId).map(_.creatorId)

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

  override def canModifyReview(userId: String, reviewId: UUID) = run {
    reviewCreator(lift(reviewId)).contains(lift(userId))
  }.provide(layer)

  override def canModifyComment(userId: String, reviewId: UUID, commentId: Long) = run {
    comment
      .filter(_.commentId == lift(commentId))
      .filter(_.reviewId == lift(reviewId))
      .map(_.commenter)
      .contains(lift(userId))
  }.provide(layer)

  override def canMakeComment(userId: String, reviewId: UUID): IO[SQLException, Boolean] = run {
    allUsersWithWriteAccess(lift(reviewId)).filter(_ == lift(userId))
  }.map(_.nonEmpty).provide(layer)

}
