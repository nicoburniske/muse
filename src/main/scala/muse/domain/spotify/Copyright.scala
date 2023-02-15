package muse.domain.spotify

import zio.json.*

final case class Copyright(text: String, `type`: String)

object Copyright {
  given JsonDecoder[Copyright] = DeriveJsonDecoder.gen[Copyright]
}
