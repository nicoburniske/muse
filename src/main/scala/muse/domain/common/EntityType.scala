package muse.domain.common

import zio.json.{JsonDecoder, JsonEncoder}
import caliban.schema.{ArgBuilder, Schema}

enum EntityType:
  case Album, Artist, Playlist, Track

