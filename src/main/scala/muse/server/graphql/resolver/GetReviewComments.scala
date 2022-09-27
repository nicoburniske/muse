package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Comment
import muse.service.persist.DatabaseService
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ZIO}

import java.util.UUID

case class GetReviewComments(reviewId: UUID) extends Request[Nothing, List[Comment]]

object GetReviewComments {

  def query(reviewId: UUID) = ZQuery.fromRequest(GetReviewComments(reviewId))(CommentDataSource)

  // TODO: incorporate permissions
  val CommentDataSource: DataSource[DatabaseService, GetReviewComments] =
    DataSource.Batched.make("ReviewCommentsDataSource") { (requests: Chunk[GetReviewComments]) =>
      requests.toList match
        case req :: Nil =>
          DatabaseService
            .getReviewComments(req.reviewId)
            .fold(
              e => CompletedRequestMap.empty.insert(req)(Left(e)),
              comments => CompletedRequestMap.empty.insert(req)(Right(comments.map(Comment.fromTable)))
            )
        case reqs       =>
          val ids = reqs.map(_.reviewId)
          for {
            maybeComments <- DatabaseService.getAllReviewComments(ids).either
            _             <- if (maybeComments.isRight) ZIO.logInfo(s"Retrieved reviews: ${ids.mkString(", ")}")
                             else ZIO.logInfo(s"Failed to retrieve reviews: ${ids.mkString(",")}")
          } yield {
            maybeComments match
              case Left(value)        =>
                reqs.foldLeft(CompletedRequestMap.empty)((map, r) => map.insert(r)(Left(value)))
              case Right(allComments) =>
                val grouped = allComments.groupBy(_.reviewId)
                reqs.foldLeft(CompletedRequestMap.empty) { (map, r) =>
                  val asComments = grouped.getOrElse(r.reviewId, Nil).map(Comment.fromTable)
                  map.insert(r)(Right(asComments))
                }
          }
    }

}
