package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseService
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

case class GetReview(reviewId: UUID) extends Request[SQLException, Option[Review]]

object GetReview {
  def query(reviewId: UUID) = ZQuery.fromRequest(GetReview(reviewId))(ReviewDataSource)


  // TODO: create batch data source for review by id.
  // TODO: Incorporate permissions
  // TODO: Add user id to request so we can infer user permissions!
  // TODO: should we give errors or just not return the review?
  val ReviewDataSource: DataSource[DatabaseService, GetReview] =
    DataSource.fromFunctionZIO("ReviewDataSource") { g =>
      DatabaseService.getReview(g.reviewId).map(_.map { case (review, entity) => Review.fromTable(review, entity) })
    }

}
