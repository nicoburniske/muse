package muse.domain.spotify

import zio.json.*

final case class MultiAlbum(albums: Vector[Album])

object MultiAlbum {
  given decoder: JsonDecoder[MultiAlbum] = DeriveJsonDecoder.gen[MultiAlbum]
}
