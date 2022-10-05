package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Album
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ZIO}

case class GetAlbum(id: String) extends Request[Throwable, Album]

object GetAlbum {
  val MAX_ALBUMS_PER_REQUEST = 20

  def query(albumId: String) = ZQuery.fromRequest(GetAlbum(albumId))(AlbumDataSource)

  val AlbumDataSource: DataSource[RequestSession[SpotifyService], GetAlbum] =
    DataSource.Batched.make("AlbumDataSource") { (reqs: Chunk[GetAlbum]) =>
      reqs.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          RequestSession
            .get[SpotifyService].flatMap(_.getAlbum(head.id))
            .fold(
              e => CompletedRequestMap.empty.insert(head)(Left(e)),
              a => CompletedRequestMap.empty.insert(head)(Right(Album.fromSpotify(a))))
            .addTimeLog("Retrieved Album")
        case _           =>
          ZIO
            .foreachPar(reqs.grouped(MAX_ALBUMS_PER_REQUEST).toVector) { reqs =>
              val ids = reqs.map(_.id)
              RequestSession
                .get[SpotifyService].flatMap(_.getAlbums(ids))
                .either.map(reqs -> _)
            }
            .map { res =>
              res.foldLeft(CompletedRequestMap.empty) {
                case (map: CompletedRequestMap, (reqs, result)) =>
                  result match
                    case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                    case Right(albums)   =>
                      val grouped = albums.map(Album.fromSpotify).groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result =
                          grouped.get(req.id).fold(Left(InvalidEntity(req.id, EntityType.Album)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
            .addTimeLog(s"Retrieved Albums ${reqs.size}")
    }

}
