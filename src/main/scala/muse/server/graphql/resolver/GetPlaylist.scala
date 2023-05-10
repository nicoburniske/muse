package muse.server.graphql.resolver

import muse.server.graphql.Helpers.getSpotify
import muse.server.graphql.subgraph.Playlist
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.{Reloadable, ZIO}
import zio.metrics.Metric
import zio.query.{DataSource, Request, ZQuery}

import java.time.temporal.ChronoUnit

// TODO: link back to user?
case class GetPlaylist(id: String) extends Request[Throwable, Playlist]

object GetPlaylist {
  type Env = Reloadable[SpotifyService]
  def query(playlistId: String) = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)

  def metric = Utils.timer("GetPlaylist", ChronoUnit.MILLIS)

  val PlaylistDataSource: DataSource[Env, GetPlaylist] =
    DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
      getSpotify
        .flatMap(_.getPlaylist(req.id))
        .map(Playlist.fromSpotify)
        @@ metric.trackDuration
    }

}
