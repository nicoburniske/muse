package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Artist
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetArtist(id: String) extends Request[Throwable, Artist]

object GetArtist {
  val MAX_ARTISTS_PER_REQUEST = 20

  def query(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  val ArtistDataSource: DataSource[SpotifyService, GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      reqs.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          SpotifyService
            .getArtist(head.id)
            .fold(
              e => CompletedRequestMap.empty.insert(head)(Left(e)),
              a => CompletedRequestMap.empty.insert(head)(Right(Artist.fromSpotify(a))))
            .addTimeLog("Retrieved Artist")
        case _           =>
          ZIO
            .foreachPar(reqs.grouped(MAX_ARTISTS_PER_REQUEST).toVector) { reqs =>
              SpotifyService.getArtists(reqs.map(_.id)).map(_.map(Artist.fromSpotify)).either.map(reqs -> _)
            }
            .map { res =>
              res.foldLeft(CompletedRequestMap.empty) {
                case (map: CompletedRequestMap, (reqs, result)) =>
                  result match
                    case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                    case Right(tracks)   =>
                      val grouped = tracks.groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result =
                          grouped.get(req.id).fold(Left(InvalidEntity(req.id, EntityType.Artist)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
            .addTimeLog(s"Retrieved Artists ${reqs.size}")
    }

}
