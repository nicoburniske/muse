package muse.server.graphql.subgraph

import muse.domain.spotify
import java.time.Instant

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: SpotifyUser,
    isLocal: Boolean,
    track: Track
)

object PlaylistTrack {
  def fromSpotify(t: spotify.PlaylistTrack) =
    PlaylistTrack(
      t.addedAt,
      SpotifyUser.missingSome(t.addedBy.id, t.addedBy.href, t.addedBy.uri, t.addedBy.externalUrls),
      t.isLocal,
      Track.fromSpotify(t.track)
    )
}
