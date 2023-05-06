package muse.domain.spotify

import zio.json.ast.JsonCursor
import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class AudioSegment(
    start: Double,
    duration: Double,
    confidence: Double,
    loudnessStart: Double,
    loudnessMax: Double,
    loudnessMaxTime: Double,
    loudnessEnd: Option[Double],
    pitches: List[Double],
    timbre: List[Double]
)

object AudioSegment {
  given JsonDecoder[AudioSegment] = DeriveJsonDecoder.gen[AudioSegment]
}
