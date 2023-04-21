package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Comment
import muse.service.persist.DatabaseService
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

object GetComment {
  case class CommentRequest(id: Long) extends Request[SQLException, Option[Comment]]

  def query(id: Long): ZQuery[DatabaseService, SQLException, Option[Comment]] =
    ZQuery.fromRequest(CommentRequest(id))(commentDataSource)

  // TODO: make this batched data source.
  val commentDataSource: DataSource[DatabaseService, CommentRequest] =
    DataSource.fromFunctionZIO("GetCommentEntities")(req =>
      DatabaseService.getComment(req.id).map { maybeCommentTuple =>
         maybeCommentTuple.map(Comment.fromTable.tupled)
      })
}
