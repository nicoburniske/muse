package muse.server.graphql.resolver

import muse.server.graphql.subgraph.Track
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.query.{Request, ZQuery}

// TODO: incorporate region/country.
case class GetArtistTopTracks(artistId: String) extends Request[Throwable, List[Track]]

object GetArtistTopTracks {
  def query(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getArtistTopTracks(artistId)).map(_.map(Track.fromSpotify(_)).toList)
}
