package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Artist
import muse.server.graphql.Helpers.*
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.{Reloadable, ZIO}
import zio.metrics.Metric
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class GetArtist(id: String) extends Request[Throwable, Artist]

object GetArtist {
  type Env = Reloadable[SpotifyService]
  val MAX_ARTISTS_PER_REQUEST = 20

  def query(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  def metric = Utils.timer("GetArtist", ChronoUnit.MILLIS)

  val ArtistDataSource: DataSource[Env, GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_ARTISTS_PER_REQUEST,
          req => getSpotify.flatMap(_.getArtist(req.id)),
          reqs => getSpotify.flatMap(_.getArtists(reqs.map(_.id))),
          Artist.fromSpotify,
          _.id,
          _.id
        ) @@ metric.trackDuration
    }

}
