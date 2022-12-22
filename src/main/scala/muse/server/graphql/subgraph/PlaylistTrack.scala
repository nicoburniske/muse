package muse.server.graphql.subgraph

import muse.domain.spotify
import muse.server.graphql.resolver.GetPlaylist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: User,
    isLocal: Boolean,
    track: Track,
    playlist: ZQuery[RequestSession[SpotifyService], Nothing, Playlist]
)

object PlaylistTrack {
  def fromSpotify(t: spotify.PlaylistTrack, playlistId: String) =
    PlaylistTrack(
      t.addedAt,
      User.missingSome(t.addedBy.id, t.addedBy.displayName, t.addedBy.href, t.addedBy.uri, t.addedBy.externalUrls),
      t.isLocal,
      Track.fromSpotify(t.track),
      // TODO: This should never fail because playlist should be in cache. hopefully?
      GetPlaylist.query(playlistId).orDie
    )
}
