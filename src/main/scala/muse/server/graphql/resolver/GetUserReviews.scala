package muse.server.graphql.resolver

import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.Review
import muse.service.persist.DatabaseService
import zio.*
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException

case class GetUserReviews(userId: UserId, which: WhichReviews) extends Request[SQLException, List[Review]]

sealed trait WhichReviews

// All reviews user has access to.
case object All extends WhichReviews

// User's created reviews.
case object Owned extends WhichReviews

// Someone else's reviews.
case object WithAccess extends WhichReviews

object GetUserReviews {
  type Env = DatabaseService with UserSession
  def query(userId: UserId): ZQuery[Env, Throwable, List[Review]] = for {
    user   <- ZQuery.fromZIO(ZIO.service[UserSession])
    which   = if (user.userId == userId) All else WithAccess
    result <- query(userId, which)
  } yield result

  def query(userId: UserId, which: WhichReviews) = ZQuery.fromRequest(GetUserReviews(userId, which))(UserReviewsDataSource)

  val UserReviewsDataSource: DataSource[Env, GetUserReviews] =
    DataSource.fromFunctionZIO("UserReviewsDataSource") { req =>
      (req.which match {
        case All        => DatabaseService.getAllUserReviews(req.userId)
        case Owned      => DatabaseService.getUserReviews(req.userId)
        case WithAccess =>
          for {
            viewer  <- ZIO.service[UserSession].map(_.userId)
            reviews <- DatabaseService.getUserReviewsExternal(req.userId, viewer)
          } yield reviews
      }).map(_.map(Review.fromTable))
    }
}
