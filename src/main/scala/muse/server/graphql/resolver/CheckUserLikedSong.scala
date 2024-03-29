package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.server.graphql.Helpers.getSpotify
import muse.domain.error.InvalidEntity
import muse.domain.session.UserSession
import muse.server.graphql.subgraph.User
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import muse.utils.Utils.*
import zio.{Reloadable, ZIO}
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class CheckUserLikedSong(trackId: String) extends Request[Nothing, Boolean]

object CheckUserLikedSong {
  val MAX_PER_REQUEST = 50
  type Env = Reloadable[SpotifyService]
  def query(trackId: String) =
    ZQuery.fromRequest(CheckUserLikedSong(trackId))(dataSource)

  def metric = Utils.timer("GetPlaylist", ChronoUnit.MILLIS)

  val dataSource: DataSource[Env, CheckUserLikedSong] =
    DataSource.Batched.make("CheckUserLikedSong") { req =>
      ZIO
        .foreachPar(req.toVector.grouped(MAX_PER_REQUEST).toVector) { batch =>
          getSpotify
            .flatMap(_.checkUserSavedTracks(batch.map(_.trackId)))
            .either.map(batch -> _)
        }.map { results =>
          val processed = results.flatMap {
            case (reqs, Left(error))  => reqs.map(_ -> Left(error))
            case (reqs, Right(likes)) =>
              val grouped = likes
                .groupMap(_._1)(_._2)
                .filter(_._2.nonEmpty)
                .map(entry => entry._1 -> entry._2.head)
              reqs.map { req =>
                req ->
                  grouped
                    .get(req.trackId)
                    .fold(Left(InvalidEntity(req.trackId, EntityType.Track)))(Right(_))
              }
          }

          processed.foldLeft(CompletedRequestMap.empty) { case (acc, (req, result)) => acc.insert(req)(result) }
        } @@ metric.trackDuration
    }
}
