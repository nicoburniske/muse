package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Playlist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.metrics.Metric
import zio.query.{DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

// TODO: link back to user?
case class GetPlaylist(id: String) extends Request[Throwable, Playlist]

object GetPlaylist {
  def query(playlistId: String) = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)

  def metric = Utils.timer("GetPlaylist", ChronoUnit.MILLIS)

  val PlaylistDataSource: DataSource[RequestSession[SpotifyService], GetPlaylist] =
    DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
      RequestSession
        .get[SpotifyService]
        .flatMap(_.getPlaylist(req.id))
        .map(Playlist.fromSpotify)
        @@ metric.trackDuration
    }

}
