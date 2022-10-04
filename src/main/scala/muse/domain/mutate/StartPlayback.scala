package muse.domain.mutate

import muse.domain.common.EntityType

// TODO: only albums, artists & playlists.
case class Context(entityType: EntityType, entityId: String)

case class PositionOffset(context: Context, position: Int)
case class EntityOffset(outer: Context, inner: Context)

case class StartPlayback(
    deviceId: Option[String],
    uris: Option[List[String]],
    positionOffset: Option[PositionOffset],
    entityOffset: Option[EntityOffset],
    positionMs: Option[Int])
