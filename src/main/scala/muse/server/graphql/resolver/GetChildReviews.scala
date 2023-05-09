package muse.server.graphql.resolver

import muse.domain.table
import muse.domain.table.{ReviewEntity, ReviewLink}
import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseService
import muse.utils.Utils
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.temporal.ChronoUnit
import java.util.UUID

case class GetChildReviews(reviewId: UUID) extends Request[SQLException, List[Review]]

object GetChildReviews {
  type Env = DatabaseService

  val MAX_CHILD_REVIEWS_PER_REQUEST = 20

  def query(reviewId: UUID) = ZQuery.fromRequest(GetChildReviews(reviewId))(ChildReviewDataSource)

  def metric = Utils.timer("GetChildReviews", ChronoUnit.MILLIS)

  val ChildReviewDataSource: DataSource[DatabaseService, GetChildReviews] =
    DataSource.Batched.make("GetChildReviews") { reqs =>
      ZIO
        .foreachPar(reqs.grouped(MAX_CHILD_REVIEWS_PER_REQUEST).toVector) { batch =>
          DatabaseService
            .getAllChildReviews(batch.map(_.reviewId).toList)
            .either
            .map(batch.toList -> _)
        }.map {
          (results: Vector[
            (List[GetChildReviews], Either[SQLException, List[(ReviewLink, table.Review, Option[ReviewEntity])]])]) =>
            val processed = results.flatMap {
              // In error case all requests get error.
              case (reqs, Left(error))         =>
                reqs.map(_ -> Left(error))
              // In success case group by reviewId.
              case (reqs, Right(childReviews)) =>
                val grouped = childReviews
                  .groupBy(_._1.parentReviewId)
                  .view
                  // We also need to sort by linkIndex.
                  .mapValues(_.sortBy(_._1.linkIndex))
                  .mapValues(_.map { case (_, review, entity) => Review.fromTable(review, entity) })
                  .toMap
                // Review can have no children so include empty list as base case.
                reqs.map { req => req -> Right(grouped.getOrElse(req.reviewId, List.empty)) }
            }

            processed.foldLeft(CompletedRequestMap.empty) { case (acc, (req, result)) => acc.insert(req)(result) }
        } @@ metric.trackDuration
    }
}
