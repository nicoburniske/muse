package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Track
import muse.server.graphql.Helpers.getSpotify
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.{Reloadable, ZIO}
import zio.metrics.Metric
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class GetTrack(id: String) extends Request[Throwable, Track]

object GetTrack {
  type Env = Reloadable[SpotifyService]
  val MAX_TRACKS_PER_REQUEST = 50

  def query(trackId: String) = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)

  def metric = Utils.timer("GetTrack", ChronoUnit.MILLIS)

  val TrackDataSource: DataSource[Env, GetTrack] =
    DataSource.Batched.make("TrackDataSource") { reqs =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_TRACKS_PER_REQUEST,
          req => getSpotify.flatMap(_.getTrack(req.id)),
          reqs => getSpotify.flatMap(_.getTracks(reqs.map(_.id))),
          Track.fromSpotify,
          _.id,
          _.id
        ) @@ metric.trackDuration
    }

}
