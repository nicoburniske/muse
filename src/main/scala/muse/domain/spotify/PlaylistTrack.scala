package muse.domain.spotify

import zio.json.*

import java.time.Instant

@jsonMemberNames(SnakeCase)
final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: PlaylistUser,
    isLocal: Boolean,
    track: Track
)

object PlaylistTrack {
  given decodePlaylistTrack: JsonDecoder[PlaylistTrack] = DeriveJsonDecoder.gen[PlaylistTrack]
}
