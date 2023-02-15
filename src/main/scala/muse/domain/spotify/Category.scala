package muse.domain.spotify

import zio.json.*

final case class Category(
    href: String,
    icons: List[Image],
    id: String,
    name: String
)

final case class MultiCategory(categories: Paging[Category])

object Category {
  given JsonDecoder[Category]      = DeriveJsonDecoder.gen[Category]
  given JsonDecoder[MultiCategory] = DeriveJsonDecoder.gen[MultiCategory]
}
