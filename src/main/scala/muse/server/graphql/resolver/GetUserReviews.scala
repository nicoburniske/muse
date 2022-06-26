package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseOps
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException

case class GetUserReviews(userId: String) extends Request[SQLException, List[Review]]

object GetUserReviews {
  def query(userId: String) = ZQuery.fromRequest(GetUserReviews(userId))(UserReviewsDataSource)

  val UserReviewsDataSource: DataSource[DatabaseOps, GetUserReviews] =
    DataSource.fromFunctionZIO("UserReviewsDataSource") { req =>
      val reviews = DatabaseOps.getUserReviews(req.userId)
      reviews.map(_.map(Review.fromTable))
    }
}
