package muse.domain.spotify

import zio.json.JsonDecoder

final case class Confidence(value: Double)

object Confidence {
  given JsonDecoder[Confidence] = JsonDecoder[Double].map(Confidence(_))
}
