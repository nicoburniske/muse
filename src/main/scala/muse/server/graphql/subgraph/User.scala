package muse.server.graphql.subgraph

import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

final case class User(
    id: String,
    reviews: ZQuery[DatabaseOps, Throwable, List[Review]],
    spotifyProfile: ZQuery[SpotifyService, Throwable, SpotifyProfile]
)
