package muse.server.graphql.subgraph

import muse.server.graphql.resolver.GetSearch.PaginationResult

final case class SearchResult(
    albums: Option[PaginationResult[Album]],
    artists: Option[PaginationResult[Artist]],
    playlists: Option[PaginationResult[Playlist]],
    tracks: Option[PaginationResult[Track]]
)
