package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Track
import muse.service.spotify.SpotifyService
import muse.utils.Utils
import zio.ZIO
import zio.query.ZQuery

import java.time.temporal.ChronoUnit

object GetAlbumTracks {
  val MAX_TRACKS_PER_REQUEST = 50

  def metric = Utils.timer("GetPlaylistTracks", ChronoUnit.MILLIS)

  /**
   * Retrieves tracks for the given album.
   *
   * @param albumId
   *   the album's id
   * @param numTracks
   *   if known, pagination can occur in parallel
   * @return
   *   the tracks from the album
   */
  def query(albumId: String, numTracks: Option[Int]): ZQuery[SpotifyService, Throwable, List[Track]] =
    ZQuery.fromZIO((numTracks match {
      case None        =>
        ZIO
          .service[SpotifyService]
          .flatMap(_.getAllAlbumTracks(albumId))
          .map(_.map(t => Track.fromSpotifySimple(t, Some(albumId))).toList)
      case Some(total) =>
        ZIO
          .foreachPar((0 until total).grouped(MAX_TRACKS_PER_REQUEST).map(_.start).toList) { r =>
            ZIO
              .service[SpotifyService]
              .flatMap(_.getSomeAlbumTracks(albumId, Some(MAX_TRACKS_PER_REQUEST), Some(r)))
              .map(_.items)
              .map(_.map(t => Track.fromSpotifySimple(t, Some(albumId))))
          }
          .map(_.flatten.toList)
    }) @@ metric.trackDuration)
}
