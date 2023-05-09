package muse.server.graphql.resolver

import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.Comment
import muse.service.persist.DatabaseService
import zio.*
import zio.query.{DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

object GetComment {
  val MAX_COMMENT_PER_REQUEST = 200
  type Env = DatabaseService with UserSession

  final case class CommentRequest(id: Long) extends Request[SQLException, Option[Comment]]

  def query(id: Long): ZQuery[Env, SQLException, Option[Comment]] =
    ZQuery.fromRequest(CommentRequest(id))(commentDataSource)

  // TODO: make this batched data source.
  val commentDataSource: DataSource[Env, CommentRequest] =
    DataSource.fromFunctionZIO("GetComment") { req =>
      for {
        userId  <- ZIO.service[UserSession].map(_.userId)
        comment <-
          DatabaseService
            .getComment(req.id, userId)
            .map { maybeCommentTuple => maybeCommentTuple.map(Comment.fromTable.tupled) }
      } yield comment
    }

  final case class CommentChildrenRequest(id: Long) extends Request[SQLException, List[Comment]]

  def queryAllChildren(id: Long): ZQuery[Env, SQLException, List[Comment]] =
    ZQuery.fromRequest(CommentChildrenRequest(id))(allChildrenDataSource)

  val allChildrenDataSource: DataSource[Env, CommentChildrenRequest] =
    DataSource.fromFunctionZIO("AllCommentChildrenDataSource") { req =>
      for {
        userId   <- ZIO.service[UserSession].map(_.userId)
        children <- DatabaseService.getAllCommentChildren(req.id, userId).map { Comment.fromTableRows }
      } yield children
    }
}
