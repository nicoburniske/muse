package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class SearchResult(
    albums: Option[Paging[Album]],
    artists: Option[Paging[Artist]],
    playlists: Option[Paging[UserPlaylist]],
    tracks: Option[Paging[Track]]
)

object SearchResult {
  given searchDecoder: JsonDecoder[SearchResult] = DeriveJsonDecoder.gen[SearchResult]
}
