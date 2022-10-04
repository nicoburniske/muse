package muse.server.graphql.resolver

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.User
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils.*
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class CheckUserLikedSong(trackId: String) extends Request[Nothing, Boolean]

object CheckUserLikedSong {
  def query(trackId: String) = ZQuery.fromRequest(CheckUserLikedSong(trackId))(dataSource)

  val dataSource: DataSource[SpotifyService, CheckUserLikedSong] =
    DataSource.Batched.make("CheckUserLikedSong") { req =>
      if (req.isEmpty) {
        ZIO.succeed(CompletedRequestMap.empty)
      } else {
        ZIO
          .foreachPar(req.distinct.map(_.trackId).toVector.grouped(50).toVector) { trackIds =>
            SpotifyService.checkUserSavedTracks(trackIds).either.map(trackIds -> _)
          }.map {
            _.foldLeft(CompletedRequestMap.empty) {
              case (map, (requests, result)) =>
                result match
                  case error @ Left(_)  =>
                    requests.foldLeft(map)((map, req) => map.insert(CheckUserLikedSong(req))(error))
                  case Right(songLikes) =>
                    // TODO: what happens when track id is not found?
                    songLikes.foldLeft(map) { (map, req) => map.insert(CheckUserLikedSong(req._1))(Right(req._2)) }
            }
          }.addTimeLog(s"Retrieved user likes for ${req.size} track(s)")
      }
    }
}
