package muse.domain.common

import zio.json.{JsonDecoder, JsonEncoder}

// Ordinal starts with 0.
enum EntityType:
  case Album
  case Artist
  case Playlist
  case Track

object EntityType {
  given decoder: JsonDecoder[EntityType] = JsonDecoder[Int].map {
    EntityType.fromOrdinal(_)
  }
  given encoder: JsonEncoder[EntityType] = JsonEncoder[Int].contramap(_.ordinal)
}
