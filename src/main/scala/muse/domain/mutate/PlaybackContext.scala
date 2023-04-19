package muse.domain.mutate

import caliban.schema.Annotations.GQLDescription
import muse.domain.common.EntityType

import caliban.schema.{ArgBuilder, Schema}

final case class PlayOffsetContextInput(input: PlayOffsetContext)
final case class PlayOffsetContext(offset: PositionOffset, positionMs: Option[Int], deviceId: Option[String])
final case class PlayEntityContextInput(input: PlayEntityContext)
final case class PlayEntityContext(offset: EntityOffset, positionMs: Option[Int], deviceId: Option[String])

// TODO: only albums, artists & playlists.
final case class PositionOffset(context: Context, position: Int)
final case class EntityOffset(outer: Context, inner: Context)

final case class Context(entityType: EntityType, entityId: String)

final case class PlayInput(input: Play)
final case class Play(deviceId: Option[String])
final case class PlayTracksInput(input: PlayTracks)
final case class PlayTracks(trackIds: List[String], positionMs: Option[Int], deviceId: Option[String])
