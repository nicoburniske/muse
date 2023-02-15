package muse.domain.spotify

import zio.json.*

final case class CollectionLink(href: String, total: Int)

object CollectionLink {
  given JsonDecoder[CollectionLink] = DeriveJsonDecoder.gen[CollectionLink]
}
