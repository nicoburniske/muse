package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, *}

final case class MultiAlbum(albums: Vector[Album])

object MultiAlbum {
  given decoder: JsonDecoder[MultiAlbum] = DeriveJsonDecoder.gen[MultiAlbum]
}
