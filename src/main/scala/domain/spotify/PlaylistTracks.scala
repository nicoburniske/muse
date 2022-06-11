package domain.spotify
import zio.json.*

final case class PlaylistTracks(
    href: String,
    total: Int
)

object PlaylistTracks {
      given decodePlaylistTracks: JsonDecoder[PlaylistTracks] = DeriveJsonDecoder.gen[PlaylistTracks]
}
