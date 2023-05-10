package muse.server.graphql.resolver

import muse.domain.spotify.AudioAnalysis
import muse.server.graphql.Helpers.getSpotify
import muse.server.graphql.subgraph.Playlist
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.*
import zio.metrics.Metric
import zio.query.{DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

case class GetTrackAudioAnalysis (id: String) extends Request[Throwable, AudioAnalysis]

object GetTrackAudioAnalysis {
  type Env = Reloadable[SpotifyService] 
  def query(playlistId: String) = ZQuery.fromRequest(GetTrackAudioAnalysis(playlistId))(AudioAnalysisDataSource)

  def metric = Utils.timer("GetTrackAudioAnalysis", ChronoUnit.MILLIS)

  val AudioAnalysisDataSource: DataSource[Env, GetTrackAudioAnalysis] =
    DataSource.fromFunctionZIO("AudioAnalysisDataSource") { req =>
        getSpotify.flatMap(_.getTrackAudioAnalysis(req.id))
        @@ metric.trackDuration
    }
}

