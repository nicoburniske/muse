package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Album
import muse.service.spotify.SpotifyService
import zio.query.{Request, ZQuery}

case class GetArtistAlbums(artistId: String) extends Request[Throwable, List[Album]]

object GetArtistAlbums {
  // TODO: Consider some parallelism
  def query(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getAllArtistAlbums(artistId)).map(_.map(Album.fromSpotify).toList)
}
