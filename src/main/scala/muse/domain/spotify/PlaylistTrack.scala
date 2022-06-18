package muse.domain.spotify

import zio.json.*

import java.time.Instant

final case class PlaylistTrack(
    @jsonField("added_at")
    addedAt: Instant,
    @jsonField("added_by")
    addedBy: User,
    @jsonField("is_local")
    isLocal: Boolean,
    track: Track
)

object PlaylistTrack {
  given decodePlaylistTrack: JsonCodec[PlaylistTrack] = DeriveJsonCodec.gen[PlaylistTrack]
}
