package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Artist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.ZIO
import zio.metrics.Metric
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class GetArtist(id: String) extends Request[Throwable, Artist]

object GetArtist {
  val MAX_ARTISTS_PER_REQUEST = 20

  def query(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  def metric = Utils.timer("GetArtist", ChronoUnit.MILLIS)

  val ArtistDataSource: DataSource[SpotifyService, GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_ARTISTS_PER_REQUEST,
          req => ZIO.service[SpotifyService].flatMap(_.getArtist(req.id)),
          reqs => ZIO.service[SpotifyService].flatMap(_.getArtists(reqs.map(_.id))),
          Artist.fromSpotify,
          _.id,
          _.id
        ) @@ metric.trackDuration
    }

}
