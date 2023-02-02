package muse.server.graphql.resolver

import muse.server.graphql.subgraph.PlaylistTrack
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.query.{DataSource, Request, ZQuery}
import zio.stream.ZStream
import zio.{IO, Schedule, TaskLayer, UIO, ZIO, ZLayer, durationInt}

import java.time.temporal.ChronoUnit

case class GetPlaylistTracks(playlistId: String, numTracks: Int) extends Request[Throwable, List[PlaylistTrack]]

object GetPlaylistTracks {
  val MAX_PLAYLIST_TRACKS_PER_REQUEST = 100
  
  def metric = Utils.timer("GetPlaylistTracks", ChronoUnit.MILLIS)

  def query(playlistId: String, numTracks: Int) =
    ZQuery.fromRequest(GetPlaylistTracks(playlistId, numTracks))(PlaylistTrackDataSource)

  def stream(playlistId: String, numTracks: Int) =
    ZStream
      .fromIterable(requestOffsets(numTracks))
      .mapZIOPar(5)(offset => getTracks(playlistId, offset))
      .flatMap(ZStream.fromIterable(_))

  val PlaylistTrackDataSource: DataSource[RequestSession[SpotifyService], GetPlaylistTracks] =
    DataSource.fromFunctionZIO("PlaylistTrackDataSource") { req =>
      ZIO
        .foreachPar(requestOffsets(req.numTracks))(offset => getTracks(req.playlistId, offset))
        .map(_.flatten.toList)
        @@ metric.trackDuration
    }

  private def requestOffsets(totalTracks: Int) =
    (0 until totalTracks)
      .grouped(MAX_PLAYLIST_TRACKS_PER_REQUEST)
      .map(_.start)
      .toList

  private def getTracks(playlistId: String, offset: Int) = {
    RequestSession
      .get[SpotifyService]
      .flatMap(_.getSomePlaylistTracks(playlistId, MAX_PLAYLIST_TRACKS_PER_REQUEST, Some(offset)))
      .map(_.items)
      .map(_.map(PlaylistTrack.fromSpotify(_, playlistId)))
  }
}
