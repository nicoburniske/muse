package muse.server.graphql.subgraph

import muse.domain.common.Types.UserId
import muse.domain.spotify
import muse.server.graphql.resolver.{GetPlaylist, GetUser}
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

import java.time.Instant

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: User,
    isLocal: Boolean,
    track: Track,
    playlist: ZQuery[SpotifyService, Nothing, Playlist]
)

object PlaylistTrack {
  // When track is added by Spotify (generated playlist) the user is empty.
  def fromSpotify(t: spotify.PlaylistTrack, playlistId: String) = {
    val user = {
      if (t.addedBy.id.isBlank)
        GetUser.queryByUserId(UserId("spotify"))
      else
        User.missingSome(UserId(t.addedBy.id), t.addedBy.displayName, t.addedBy.href, t.addedBy.uri, t.addedBy.externalUrls)
    }

    PlaylistTrack(
      t.addedAt,
      user,
      t.isLocal,
      Track.fromSpotify(t.track),
      // TODO: This should never fail because playlist should be in cache. hopefully?
      GetPlaylist.query(playlistId).orDie
    )
  }
}
