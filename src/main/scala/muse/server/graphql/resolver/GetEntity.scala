package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.server.graphql.subgraph.ReviewEntity
import muse.service.spotify.SpotifyService
import zio.query.ZQuery
import zio.*

object GetEntity {
  type Env = Reloadable[SpotifyService]
  def query(entityId: String, entityType: EntityType): ZQuery[Env, Throwable, ReviewEntity] =
    entityType match
      case EntityType.Album    => GetAlbum.query(entityId)
      case EntityType.Artist   => GetArtist.query(entityId)
      case EntityType.Playlist => GetPlaylist.query(entityId)
      case EntityType.Track    => GetTrack.query(entityId)
}
