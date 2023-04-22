package muse.server.graphql.resolver

import muse.domain.spotify.AudioAnalysis
import muse.server.graphql.subgraph.Playlist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.metrics.Metric
import zio.query.{DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class GetTrackAudioAnalysis (id: String) extends Request[Throwable, AudioAnalysis]

object GetTrackAudioAnalysis {
  def query(playlistId: String) = ZQuery.fromRequest(GetTrackAudioAnalysis(playlistId))(AudioAnalysisDataSource)

  def metric = Utils.timer("GetTrackAudioAnalysis", ChronoUnit.MILLIS)

  val AudioAnalysisDataSource: DataSource[RequestSession[SpotifyService], GetTrackAudioAnalysis] =
    DataSource.fromFunctionZIO("AudioAnalysisDataSource") { req =>
      RequestSession
        .get[SpotifyService]
        .flatMap(_.getTrackAudioAnalysis(req.id))
        @@ metric.trackDuration
    }
}

