package muse.server.graphql.resolver

import muse.service.persist.DatabaseService
import zio.query.ZQuery

import java.sql.SQLException
import java.util.UUID

object GetCollaborators {
  def query(reviewId: UUID) = ZQuery.fromZIO(DatabaseService.getUsersWithAccess(reviewId))
}
