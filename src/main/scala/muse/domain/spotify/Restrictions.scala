package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder}

case class Restrictions(reason: String)

object Restrictions {
  given JsonDecoder[Restrictions] = DeriveJsonDecoder.gen[Restrictions]
}
