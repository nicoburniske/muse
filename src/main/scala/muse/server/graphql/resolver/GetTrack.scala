package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Track
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetTrack(id: String) extends Request[Throwable, Track]

object GetTrack {
  val MAX_TRACKS_PER_REQUEST = 50

  def query(trackId: String) = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)

  val TrackDataSource: DataSource[SpotifyService, GetTrack] =
    DataSource.Batched.make("TrackDataSource") { reqChunks =>
      reqChunks.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          addTimeLog("Retrieved Single Track")(
            SpotifyService
              .getTrack(head.id)
              .fold(
                e => CompletedRequestMap.empty.insert(head)(Left(e)),
                t => CompletedRequestMap.empty.insert(head)(Right(Track.fromSpotify(t)))
              )
          )
        case reqs        =>
          // TODO: make constants for max batch size.
          addTimeLog("Retrieved Multiple Tracks")(
            ZIO
              .foreachPar(reqs.grouped(MAX_TRACKS_PER_REQUEST).toVector) { reqs =>
                SpotifyService
                  .getTracks(reqs.map(_.id))
                  .map(_.map(Track.fromSpotify(_)))
                  .either
                  .map(reqs -> _)
              }
              .map { res =>
                res.foldLeft(CompletedRequestMap.empty) {
                  case (map: CompletedRequestMap, (reqs, result)) =>
                    result match
                      case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                      case Right(tracks)   =>
                        val grouped =
                          tracks.groupBy(_.id).view.mapValues(_.head)
                        reqs.foldLeft(map) { (map, req) =>
                          val result =
                            grouped.get(req.id).fold(Left(InvalidEntity(req.id, EntityType.Track)))(Right(_))
                          map.insert(req)(result)
                        }
                }
              }
          )
    }

}
