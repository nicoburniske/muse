package muse.domain.spotify
import zio.json.*

final case class PlaylistTracks(
    href: String,
    total: Int
)

object PlaylistTracks {
  given decodePlaylistTracks: JsonCodec[PlaylistTracks] = DeriveJsonCodec.gen[PlaylistTracks]
}
