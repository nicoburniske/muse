package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.domain.spotify.AudioFeatures
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
import zio.{Chunk, ZIO}

import java.time.temporal.ChronoUnit

case class GetTrackAudioFeatures(trackId: String) extends Request[Throwable, AudioFeatures]

object GetTrackAudioFeatures {
  val MAX_PER_REQUEST = 100

  def metric = Utils.timer("GetTrackAudioFeatures", ChronoUnit.MILLIS)

  def query(trackId: String) = ZQuery.fromRequest(GetTrackAudioFeatures(trackId))(AudioFeatureDataSource)

  val AudioFeatureDataSource: DataSource[RequestSession[SpotifyService], GetTrackAudioFeatures] =
    DataSource.Batched.make("AudioFeaturesDataSource") { (reqs: Chunk[GetTrackAudioFeatures]) =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_PER_REQUEST,
          req => RequestSession.get[SpotifyService].flatMap(_.getTrackAudioFeatures(req.trackId)),
          reqs => RequestSession.get[SpotifyService].flatMap(_.getTracksAudioFeatures(reqs.map(_.trackId))),
          identity,
          _.trackId,
          _.id
        ) @@ metric.trackDuration
    }

}
