package muse.server.graphql.resolver

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.User
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import muse.utils.Utils.*
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.ZIO

import java.time.temporal.ChronoUnit

case class CheckUserLikedSong(trackId: String) extends Request[Nothing, Boolean]

object CheckUserLikedSong {
  val MAX_PER_REQUEST = 50
  def query(trackId: String) =
    ZQuery.fromRequest(CheckUserLikedSong(trackId))(dataSource)

  def metric = Utils.timer("GetPlaylist", ChronoUnit.MILLIS)

  val dataSource: DataSource[RequestSession[SpotifyService], CheckUserLikedSong] =
    DataSource.Batched.make("CheckUserLikedSong") { req =>
        ZIO
          .foreachPar(req.distinct.map(_.trackId).toVector.grouped(MAX_PER_REQUEST).toVector) { trackIds =>
            RequestSession
              .get[SpotifyService]
              .flatMap(_.checkUserSavedTracks(trackIds))
              .either.map(trackIds -> _)
          }.map {
            _.foldLeft(CompletedRequestMap.empty) {
              case (map, (requests, result)) =>
                result match
                  case error @ Left(_)  =>
                    requests.foldLeft(map)((map, req) => map.insert(CheckUserLikedSong(req))(error))
                  case Right(songLikes) =>
                    songLikes.foldLeft(map) { (map, req) => map.insert(CheckUserLikedSong(req._1))(Right(req._2)) }
            }
          } @@ metric.trackDuration
    }
}
