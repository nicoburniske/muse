package muse.service.persist

import io.getquill.*
import muse.domain.common.Types.{RefreshToken, SessionId, UserId}
import muse.domain.mutate.*
import muse.domain.table.*
import zio.{Clock, IO, ZIO, ZLayer, durationInt}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

final case class DatabaseServiceLive(d: DataSource) extends DatabaseService {

  import QuillContext.{*, given}

  val layer = ZLayer.succeed(d)

  inline val userTable = "muse.user"
  inline def user      = querySchema[User](userTable)

  inline val userSessionTable = "muse.user_session"
  inline def userSession      = querySchema[UserSession](userSessionTable)

  inline val reviewTable = "muse.review"
  inline def review      = querySchema[Review](reviewTable)

  inline val reviewEntityTable = "muse.review_entity"
  inline def reviewEntity      = querySchema[ReviewEntity](reviewEntityTable)

  inline val reviewLinkTable = "muse.review_link"
  inline def reviewLink      = querySchema[ReviewLink](reviewLinkTable)

  inline val reviewAccessTable = "muse.review_access"
  inline def reviewAccess      = querySchema[ReviewAccess](reviewAccessTable)

  inline val commentTable = "muse.review_comment"
  inline def comment      = querySchema[ReviewComment](commentTable)

  inline val commentEntityTable = "muse.review_comment_entity"
  inline def commentEntity      = querySchema[ReviewCommentEntity](commentEntityTable)

  inline val commentIndexTable = "muse.review_comment_index"
  inline def commentIndex      = querySchema[ReviewCommentIndex](commentIndexTable)

  inline val commentParentChildTable = "muse.review_comment_parent_child"
  inline def commentParentChild      = querySchema[ReviewCommentParentChild](commentParentChildTable)

  /**
   * Read!
   */

  inline def userReviews(inline userId: UserId) = review.filter(_.creatorId == lift(userId))

  inline def userSharedReviews(inline userId: UserId) =
    reviewAccess
      .filter(_.userId == lift(userId))
      .join(review)
      .on((access, review) => review.reviewId == access.reviewId)
      .map(_._2)

  inline def allUserReviews(inline userId: UserId) =
    userReviews(userId) union userSharedReviews(userId)

  inline def allViewableComments(inline userId: UserId) =
    allUserReviews(userId)
      .join(comment)
      .on((review, comment) => review.reviewId == comment.reviewId)
      .map(_._2)

  override def getAllUserReviews(userId: UserId) = run {
    allUserReviews(userId)
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)

  override def getUserReviewsExternal(sourceUserId: UserId, viewerUserId: UserId) = run {
    for {
      review <- userReviews(sourceUserId).filter(_.isPublic) union userSharedReviews(viewerUserId)
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

  override def getReviewWithPermissions(reviewId: UUID, userId: UserId) = run {
    allViewableReviews(userId)
      .filter(_.reviewId == lift(reviewId))
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)
    .map(_.headOption)

  override def getReviewsWithPermissions(reviewIds: List[UUID], userId: UserId) = run {
    allViewableReviews(userId)
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
    for {
      link           <- reviewLink.filter(link => liftQuery(reviewIds.toSet).contains(link.parentReviewId))
      child          <- review.join(_.reviewId == link.childReviewId)
      reviewEntities <- reviewEntity.leftJoin(e => child.reviewId == e.reviewId)
    } yield (link, child, reviewEntities)
  }.provide(layer)

  override def getUserById(userId: UserId) = run {
    user.filter(_.userId == lift(userId))
  }.map(_.headOption).provide(layer)

  override def getUserSession(sessionId: SessionId) = run {
    userSession.filter(_.sessionId == lift(sessionId))
  }.map(_.headOption).provide(layer)

  override def getUserReviews(userId: UserId) = run {
    userReviews(userId)
      .leftJoin(reviewEntity)
      .on((review, entity) => review.reviewId == entity.reviewId)
  }.provide(layer)

  override def getComment(commentId: Long, userId: UserId) = run {
    for {
      comment       <- allViewableComments(userId).filter(_.commentId == lift(commentId))
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

  override def getComments(commentIds: List[Long], userId: UserId) = run {
    val comments = allViewableComments(userId).filter(c => liftQuery(commentIds).contains(c.commentId))
    getAllCommentData(comments)
  }.provide(layer)

  override def getAllCommentChildren(commentId: Long, userId: UserId) = run {
    val allChildrenIds: Query[Long] = sql"""
        WITH RECURSIVE children AS (
           SELECT ${lift(commentId)} AS comment_id
           UNION ALL 
           SELECT c.child_comment_id
           FROM children 
             JOIN ${lift(commentParentChildTable)} c
             ON children.comment_id = c.parent_comment_id
        )
        -- Exclude the original comment.
        SELECT * FROM children WHERE comment_id != ${lift(commentId)}
       """.as[Query[Long]]

    val childComments = allViewableComments(userId).filter(c => allChildrenIds.contains(c.commentId))
    getAllCommentData(childComments)
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
  }.provide(layer)

  override def getCommentEntities(commentId: Long) = run {
    commentEntity
      .filter(entity => entity.commentId == lift(commentId))
  }.provide(layer)

  def getFeed(userId: UserId, offset: Option[UUID], limit: Int) = {
    for {
      now        <- Clock.instant
      newestTime <- run {
                      for {
                        review <- review.filter(r => lift(offset).contains(r.reviewId))
                      } yield review.createdAt
                    }.map(_.headOption.getOrElse(now))
      result     <- run {
                      getFeedReviews(userId, newestTime)
                        .distinctOn(_.reviewId)
                        .size
                      // Need to account for how many were requested!
                    }.map(s => Math.max(0, s - limit).toInt) <&> run {
                      (for {
                        review <- getFeedReviews(userId, newestTime).sortBy(_.createdAt)(Ord.desc)
                        entity <- reviewEntity.leftJoin(_.reviewId == review.reviewId)
                      } yield (review, entity)).take(lift(limit))
                    }
    } yield result
  }.provide(layer)

  private inline def getFeedReviews(inline userId: UserId, inline newestTime: Instant) = {
    userSharedReviews(userId) union
      review.filter(_.isPublic).filter(_.creatorId != lift(userId))
  }.filter(r => r.createdAt < lift(newestTime))

  /**
   * Create!
   */

  override def createUser(userId: UserId) = run {
    user
      .insert(
        _.userId -> lift(userId)
      ).onConflictIgnore
  }.provide(layer).unit

  override def createUserSession(sessionId: SessionId, refreshToken: RefreshToken, userId: UserId) = run {
    userSession.insert(
      _.sessionId    -> lift(sessionId),
      _.refreshToken -> lift(refreshToken),
      _.userId       -> lift(userId)
    )
  }.provide(layer).unit

  override def createReview(userId: UserId, create: CreateReview) = run {
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

  override def createReviewComment(userId: UserId, create: CreateComment) = {
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
                                                 .filter(_.reviewId == lift(d.reviewId))
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

            maybeReviewId                = maybeParentIndexToDelete.map(_.reviewId)
            maybeGrandparentId           = maybeParentIndexToDelete.flatMap(_.parentCommentId)
            maybeParentIndex             = maybeParentIndexToDelete.map(_.commentIndex)
            // Move all comments on parent level down if it was deleted.
            adjustedParents: List[Long] <- run {
                                             commentIndex
                                               .filter(index => lift(maybeReviewId).contains(index.reviewId))
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
    review.filter(_.reviewId == lift(reviewId)).map(_.creatorId)

  inline def usersWithWriteAccess(inline reviewId: UUID): EntityQuery[String] =
    reviewAccess
      .filter(_.reviewId == lift(reviewId))
      .filter(_.accessLevel == lift(AccessLevel.Collaborator))
      .map(_.userId)

  inline def allUsersWithWriteAccess(inline reviewId: UUID) =
    reviewCreator(reviewId) union usersWithWriteAccess(reviewId)

  // Is review public?
  // Or does user have access to it?
  inline def allViewableReviews(inline userId: UserId) =
    review.filter(_.isPublic) union allUserReviews(userId)

  override def canModifyReview(userId: UserId, reviewId: UUID) = run {
    reviewCreator(reviewId).contains(lift(userId))
  }.provide(layer)

  override def canModifyComment(userId: UserId, reviewId: UUID, commentId: Long) = run {
    comment
      .filter(_.commentId == lift(commentId))
      .filter(_.reviewId == lift(reviewId))
      .map(_.commenter)
      .contains(lift(userId))
  }.provide(layer)

  override def canMakeComment(userId: UserId, reviewId: UUID): IO[SQLException, Boolean] = run {
    allUsersWithWriteAccess(reviewId).filter(_ == lift(userId))
  }.map(_.nonEmpty).provide(layer)

}
