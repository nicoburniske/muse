package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Playlist
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.query.{DataSource, Request, ZQuery}

// TODO: link back to user?
case class GetPlaylist(id: String) extends Request[Throwable, Playlist]

object GetPlaylist {
  def query(playlistId: String) = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)

  val PlaylistDataSource: DataSource[SpotifyService, GetPlaylist] =
    DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
      addTimeLog("Retrieved Playlist")(SpotifyService.getPlaylist(req.id).map(Playlist.fromSpotify))
    }
}
