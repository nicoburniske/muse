package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Comment
import muse.service.persist.DatabaseService
import zio.query.{DataSource, Request, ZQuery}
import java.sql.SQLException

import java.util.UUID

object GetComment {
  case class CommentRequest(id: Int) extends Request[SQLException, Option[Comment]]

  def query(id: Int): ZQuery[DatabaseService, SQLException, Option[Comment]] =
    ZQuery.fromRequest(CommentRequest(id))(commentDataSource)

  val commentDataSource: DataSource[DatabaseService, CommentRequest] =
    DataSource.fromFunctionZIO("GetCommentEntities")(req =>
      DatabaseService.getComment(req.id).map { maybeTuple =>
        val comment: Option[Comment] = maybeTuple.map { case (comment, entities) => Comment.fromTable(comment, entities) }
        comment
      }
    )
}
