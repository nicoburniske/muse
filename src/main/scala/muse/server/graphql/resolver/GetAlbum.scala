package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Album
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.metrics.Metric
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ZIO}

import java.time.temporal.ChronoUnit

case class GetAlbum(id: String) extends Request[Throwable, Album]

object GetAlbum {
  val MAX_ALBUMS_PER_REQUEST = 20

  def query(albumId: String) = ZQuery.fromRequest(GetAlbum(albumId))(AlbumDataSource)

  def metric = Utils.timer("GetAlbum", ChronoUnit.MILLIS)

  val AlbumDataSource: DataSource[RequestSession[SpotifyService], GetAlbum] =
    DataSource.Batched.make("AlbumDataSource") { (reqs: Chunk[GetAlbum]) =>
      DatasourceUtils.createBatchedDataSource(
        reqs,
        MAX_ALBUMS_PER_REQUEST,
        req => RequestSession.get[SpotifyService].flatMap(_.getAlbum(req.id)),
        reqs => RequestSession.get[SpotifyService].flatMap(_.getAlbums(reqs.map(_.id))),
        Album.fromSpotify,
        _.id,
        _.id
      ) @@ metric.trackDuration
    }
}
