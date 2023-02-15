package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class SearchResult(
    albums: Option[Paging[SimpleAlbum]],
    artists: Option[Paging[SimpleArtist]],
    playlists: Option[Paging[SimplePlaylist]],
    tracks: Option[Paging[SimpleTrack]]
)

object SearchResult {
  given searchDecoder: JsonDecoder[SearchResult] = DeriveJsonDecoder.gen[SearchResult]
}
