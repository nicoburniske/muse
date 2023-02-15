package muse.domain.spotify

import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder

final case class AudioSegment(
    start: Double,
    duration: Double,
    confidence: Confidence,
    loudness: Loudness,
    pitches: List[Double], 
    timbre: List[Double]
)

final case class Loudness(
    start: Double,
    max: Double,
    maxTime: Double,
    end: Option[Double]
)
object AudioSegment {
  given JsonDecoder[AudioSegment] = DeriveJsonDecoder.gen[AudioSegment]
  given JsonDecoder[Loudness]     = DeriveJsonDecoder.gen[Loudness]
}
