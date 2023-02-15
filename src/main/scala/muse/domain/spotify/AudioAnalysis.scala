package muse.domain.spotify

import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder

final case class AudioAnalysis(
    bars: List[TimeInterval],
    beats: List[TimeInterval],
    tatums: List[TimeInterval],
    sections: List[AudioSection],
    segments: List[AudioSegment]
)

final case class TimeInterval(start: Double, duration: Double, confidence: Confidence)

object AudioAnalysis {
  given audioAnalysisDecoder: JsonDecoder[AudioAnalysis] = DeriveJsonDecoder.gen[AudioAnalysis]
  given timeIntervalDecoder: JsonDecoder[TimeInterval]   = DeriveJsonDecoder.gen[TimeInterval]
}
