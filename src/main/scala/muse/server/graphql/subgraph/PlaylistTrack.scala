package muse.server.graphql.subgraph

import java.time.Instant

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: SpotifyUser,
    isLocal: Boolean,
    track: Track
)
