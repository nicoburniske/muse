package muse.domain.spotify

import zio.json.*

final case class ExternalIds(isrc: Option[String], ean: Option[String], upc: Option[String])

object ExternalIds {
  given JsonDecoder[ExternalIds] = DeriveJsonDecoder.gen[ExternalIds]
}
