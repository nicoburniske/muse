package muse.domain.spotify

import zio.json.*

final case class Image(height: Option[Int], width: Option[Int], url: String)

object Image {
  given decodeImage: JsonDecoder[Image] = DeriveJsonDecoder.gen[Image]
}
