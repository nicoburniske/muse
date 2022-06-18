package muse.domain.spotify

import zio.json.{DeriveJsonCodec, *}

final case class MultiAlbum(albums: Vector[Album])

object MultiAlbum {
  given decoder: JsonCodec[MultiAlbum] = DeriveJsonCodec.gen[MultiAlbum]
}
