package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Track
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.ZQuery

object GetAlbumTracks {
  val MAX_TRACKS_PER_REQUEST = 50

  /**
   * Retrieves tracks for the given album.
   *
   * @param albumId
   * the album's id
   * @param numTracks
   * if known, pagination can occur in parallel
   * @return
   * the tracks from the album
   */
  def query(albumId: String, numTracks: Option[Int]): ZQuery[SpotifyService, Throwable, List[Track]] =
    ZQuery.fromZIO((numTracks match {
      case None =>
        SpotifyService
          .getAllAlbumTracks(albumId)
          .map(_.map(t => Track.fromSpotify(t, Some(albumId))).toList)
      case Some(total) =>
        ZIO
          .foreachPar((0 until total).grouped(MAX_TRACKS_PER_REQUEST).map(_.start).toList) { r =>
            SpotifyService
              .getSomeAlbumTracks(albumId, Some(MAX_TRACKS_PER_REQUEST), Some(r))
              .map(_.items)
              .map(_.map(t => Track.fromSpotify(t, Some(albumId))))
          }
          .map(_.flatten.toList)
    }).addTimeLog("Retrieved Album Tracks", retrievedTracks => retrievedTracks.size.toString))
}
