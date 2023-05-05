package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Album
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.query.{Request, ZQuery}
import zio.ZIO

case class GetArtistAlbums(artistId: String) extends Request[Throwable, List[Album]]

object GetArtistAlbums {
  // TODO: Consider some parallelism
  def query(artistId: String): ZQuery[SpotifyService, Throwable, List[Album]] =
    ZQuery
      .fromZIO {
          ZIO.service[SpotifyService]
          .flatMap(_.getAllArtistAlbums(artistId))
      }.map(_.map(Album.fromSpotify).toList)
}
