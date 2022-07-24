package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseOps
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

// TODO: create batch data source for review by id.
// TODO: Incorporate permissions
case class GetReview(reviewId: UUID) extends Request[SQLException, Option[Review]]

object GetReview {
  def query(reviewId: UUID) = ZQuery.fromRequest(GetReview(reviewId))(ReviewDataSource)

  val ReviewDataSource: DataSource[DatabaseOps, GetReview] =
    DataSource.fromFunctionZIO("ReviewDataSource") { g =>
      DatabaseOps.getReview(g.reviewId).map(_.map(Review.fromTable))
    }

}
