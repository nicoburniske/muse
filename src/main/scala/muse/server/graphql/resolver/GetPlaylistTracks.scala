package muse.server.graphql.resolver

import muse.server.graphql.subgraph.PlaylistTrack
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.{DataSource, Request, ZQuery}

case class GetPlaylistTracks(playlistId: String, numTracks: Int)
    extends Request[Throwable, List[PlaylistTrack]]

object GetPlaylistTracks {

  def query(playlistId: String, numTracks: Int) =
    ZQuery.fromRequest(GetPlaylistTracks(playlistId, numTracks))(PlaylistTrackDataSource)

  val PlaylistTrackDataSource: DataSource[SpotifyService, GetPlaylistTracks] =
    DataSource.fromFunctionZIO("PlaylistTrackDataSource") { req =>
      addTimeLog("Retrieved all playlist tracks") {
        ZIO
          .foreachPar((0 until req.numTracks).grouped(100).map(_.start).toList) { r =>
            SpotifyService
              .getSomePlaylistTracks(req.playlistId, 100, Some(r))
              .map(_.items)
              .map(_.map(PlaylistTrack.fromSpotify))
          }
          .map(_.flatten.toList)
      }
    }
}
