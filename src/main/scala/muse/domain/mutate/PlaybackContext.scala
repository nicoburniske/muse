package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import muse.domain.common.EntityType

// TODO: only albums, artists & playlists.
case class Context(entityType: EntityType, entityId: String)

case class PositionOffset(context: Context, position: Int)
case class EntityOffset(outer: Context, inner: Context)

case class Play(deviceId: Option[String])
case class PlayTracks(trackIds: List[String], positionMs: Option[Int], deviceId: Option[String])
case class PlayOffsetContext(offset: PositionOffset, positionMs: Option[Int], deviceId: Option[String])
case class PlayEntityContext(offset: EntityOffset, positionMs: Option[Int], deviceId: Option[String])
