package muse.server.graphql.subgraph

final case class SearchResult(
    albums: List[Album],
    artists: List[Artist],
    playlists: List[Playlist],
    tracks: List[Track]
)
