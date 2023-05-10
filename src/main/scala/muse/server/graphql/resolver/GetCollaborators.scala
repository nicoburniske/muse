package muse.server.graphql.resolver

import muse.domain.table
import muse.domain.table.ReviewAccess
import muse.server.graphql.subgraph.Collaborator
import muse.service.persist.DatabaseService
import muse.utils.Utils
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.temporal.ChronoUnit
import java.util.UUID

case class GetCollaborators(reviewId: UUID) extends Request[Throwable, List[table.ReviewAccess]]

object GetCollaborators {
  type Env = DatabaseService

  val MAX_COLLABORATORS_PER_REQUEST = 20

  def query(reviewId: UUID) =
    ZQuery.fromRequest(GetCollaborators(reviewId))(CollaboratorsDataSource).map(_.map(Collaborator.fromTable))

  def metric = Utils.timer("GetCollaborators", ChronoUnit.MILLIS)

  val CollaboratorsDataSource: DataSource[Env, GetCollaborators] =
    DataSource.Batched.make("CollaboratorsDataSource") { reqs =>
      ZIO
        .foreachPar(reqs.grouped(MAX_COLLABORATORS_PER_REQUEST).toVector) { batch =>
          DatabaseService
            .getAllUsersWithAccess(batch.map(_.reviewId).toList)
            .either
            .map(batch.toList -> _)
        }.map { (results: Vector[(List[GetCollaborators], Either[SQLException, List[ReviewAccess]])]) =>
          val processed = results.flatMap {
            // In error case all requests get error.
            case (reqs, Left(error))          =>
              reqs.map(_ -> Left(error))
            // In success case group the ReviewAccess by reviewId.
            case (reqs, Right(collaborators)) =>
              val grouped = collaborators.groupBy(_.reviewId)
              // A review can have 0 collaborators so include empty list as base case.
              reqs.map { req => req -> Right(grouped.getOrElse(req.reviewId, List.empty)) }
          }

          processed.foldLeft(CompletedRequestMap.empty) { case (acc, (req, result)) => acc.insert(req)(result) }
        } @@ metric.trackDuration
    }
}
