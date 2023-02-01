package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseService
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

object GetChildReviews {
  
  val MAX_CHILD_REVIEWS_PER_REQUEST = 20
  
  case class ChildReviewsRequest(reviewId: UUID) extends Request[SQLException, List[Review]]
  
  def query(reviewId: UUID) = ZQuery.fromRequest(ChildReviewsRequest(reviewId))(ChildReviewDataSource)

  val ChildReviewDataSource: DataSource[DatabaseService, ChildReviewsRequest] =
    DataSource.fromFunctionZIO("GetChildReviews")(req =>
      DatabaseService.getChildReviews(req.reviewId).map { childReviews =>
        childReviews.map { case (review, entity) => Review.fromTable(review, entity) }
      })

}
