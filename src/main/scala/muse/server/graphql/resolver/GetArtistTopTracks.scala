package muse.server.graphql.resolver

import muse.server.graphql.Helpers.getSpotify
import muse.server.graphql.subgraph.Track
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.{Request, ZQuery}

// TODO: incorporate region/country.
case class GetArtistTopTracks(artistId: String) extends Request[Throwable, List[Track]]

object GetArtistTopTracks {
  def query(artistId: String) =
    ZQuery
      .fromZIO {
        getSpotify.flatMap(_.getArtistTopTracks(artistId))
      }.map(_.map(Track.fromSpotify).toList)
}
