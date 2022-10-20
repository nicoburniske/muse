package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import muse.domain.common.EntityType

// TODO: only albums, artists & playlists.
case class Context(entityType: EntityType, entityId: String)

case class PositionOffset(context: Context, position: Int)
case class EntityOffset(outer: Context, inner: Context)

case class PlaybackContext(
    @GQLDescription(
      "If device id is specified, playback will be transferred to that device. Otherwise, playback will be executed on user's active device.")
    deviceId: Option[String],
    uris: Option[List[String]],
    positionOffset: Option[PositionOffset],
    entityOffset: Option[EntityOffset],
    positionMs: Option[Int])
