package muse.domain.common

import zio.json.{JsonDecoder, JsonEncoder}

enum EntityType:
  case Album, Artist, Playlist, Track
