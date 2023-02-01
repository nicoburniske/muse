package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Comment
import muse.service.persist.DatabaseService
import muse.utils.Utils.*
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ZIO}

import java.sql.SQLException
import java.util.UUID

case class GetReviewComments(reviewId: UUID) extends Request[SQLException, List[Comment]]

object GetReviewComments {

  def query(reviewId: UUID) = ZQuery.fromRequest(GetReviewComments(reviewId))(CommentDataSource)

  // TODO: incorporate permissions
  val CommentDataSource: DataSource[DatabaseService, GetReviewComments] =
    DataSource.fromFunctionZIO("ReviewCommentsDataSource") { (req: GetReviewComments) =>
      DatabaseService
        .getReviewComments(req.reviewId).map { comments =>
          val grouped = comments.groupBy(_._1.id)
          grouped.map { (_, comments) =>
            val comment  = comments.map(_._1).head
            val entities = comments.map(_._2).flatten
            Comment.fromTable(comment, entities)
          }
        }.addTimeLog("GetReviewComments")
    }

}
