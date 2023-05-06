package muse.server.graphql.resolver

import caliban.*
import caliban.relay.*
import caliban.schema.ArgBuilder.auto.*
import muse.domain.session.UserSession
import muse.server.graphql.ElasticCursor
import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseService
import zio.*
import zio.query.*

import java.sql.SQLException
import java.util.{Base64, UUID}
import scala.util.{Success, Try}

/**
 * Pagination objects
 */

final case class ReviewEdge(cursor: ElasticCursor, node: Review) extends Edge[ElasticCursor, Review]

object ReviewEdge {
  def apply(review: Review): ReviewEdge = ReviewEdge(ElasticCursor(review.id.toString), review)
}

final case class ReviewConnection(pageInfo: PageInfo, edges: List[ReviewEdge]) extends Connection[ReviewEdge]

case class FeedInput(
    first: Option[Int],
    after: Option[String]
) extends ForwardPaginationArgs[ElasticCursor]
    with Request[SQLException, ReviewConnection]

object GetFeed {
  type Env = DatabaseService with UserSession
  def query(input: FeedInput) = ZQuery.fromRequest(input)(feedDataSource)
  val DEFAULT_LIMIT           = 10

  val feedDataSource: DataSource[Env, FeedInput] =
    DataSource.fromFunctionZIO("GetComment") { req =>
      val limit  = Math.min(req.first.getOrElse(DEFAULT_LIMIT), DEFAULT_LIMIT)
      val offset = req.after.flatMap(s => Try(UUID.fromString(s)).toOption)
      for {
        userId <- ZIO.service[UserSession].map(_.userId)
        feed   <-
          DatabaseService.getFeed(userId, offset, limit).map {
            case (remaining, reviews) =>
              val edges = reviews.map(Review.fromTable).map(ReviewEdge(_))
              ReviewConnection(
                PageInfo(
                  hasNextPage = remaining > 0,
                  // TODO: this might be cap.
                  hasPreviousPage = offset.isDefined && reviews.nonEmpty,
                  startCursor = edges.headOption.map(_.encodeCursor),
                  endCursor = edges.lastOption.map(_.encodeCursor)
                ),
                edges
              )
          }
      } yield feed
    }
}
