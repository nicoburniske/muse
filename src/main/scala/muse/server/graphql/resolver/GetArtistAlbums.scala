package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Album
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.{Request, ZQuery}
import muse.server.graphql.Helpers.getSpotify

case class GetArtistAlbums(artistId: String) extends Request[Throwable, List[Album]]

object GetArtistAlbums {
  // TODO: Consider some parallelism
  def query(artistId: String) =
    ZQuery
      .fromZIO {
        getSpotify
          .flatMap(_.getAllArtistAlbums(artistId))
      }.map(_.map(Album.fromSpotify).toList)
}
