package muse.domain.common

import zio.json.{JsonDecoder, JsonEncoder}
import caliban.schema.{ArgBuilder, Schema}

//import caliban.schema.Schema.auto.*
//import caliban.schema.ArgBuilder.auto.*

sealed trait EntityType derives Schema.SemiAuto, ArgBuilder {
  def ordinal = this match {
    case EntityType.Album    => 0
    case EntityType.Artist   => 1
    case EntityType.Playlist => 2
    case EntityType.Track    => 3
  }
}
object EntityType:
  case object Album    extends EntityType derives Schema.SemiAuto, ArgBuilder
  case object Artist   extends EntityType derives Schema.SemiAuto, ArgBuilder
  case object Playlist extends EntityType derives Schema.SemiAuto, ArgBuilder
  case object Track    extends EntityType derives Schema.SemiAuto, ArgBuilder

  def fromOrdinal(ordinal: Int) = ordinal match
    case 0 => EntityType.Album
    case 1 => EntityType.Artist
    case 2 => EntityType.Playlist
    case 3 => EntityType.Track

//object EntityType {
//  given Schema[Any, EntityType] = Schema.genDebug
//  given ArgBuilder[EntityType]  = ArgBuilder.gen

//  given Schema[Any, EntityType.Album]    = Schema.gen
//  given Schema[Any, EntityType.Artist]   = Schema.gen
//  given Schema[Any, EntityType.Playlist] = Schema.gen
//  given Schema[Any, EntityType.Track]    = Schema.gen
//}
