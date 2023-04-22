package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class AudioAnalysis(
    bars: List[TimeInterval],
    beats: List[TimeInterval],
    tatums: List[TimeInterval],
    sections: List[AudioSection],
    segments: List[AudioSegment]
)

final case class TimeInterval(start: Double, duration: Double, confidence: Double)

object AudioAnalysis {
  given audioAnalysisDecoder: JsonDecoder[AudioAnalysis] = DeriveJsonDecoder.gen[AudioAnalysis]
  given timeIntervalDecoder: JsonDecoder[TimeInterval]   = DeriveJsonDecoder.gen[TimeInterval]
}
