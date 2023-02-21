package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class AudioSection(
    start: Double,
    duration: Double,
    confidence: Double,
    loudness: Double,
    tempo: Int,
    tempoConfidence: Double,
    key: Double,
    keyConfidence: Double,
    mode: Modality,
    modeConfidence: Double,
    timeSignature: Double,
    timeSignatureConfidence: Double
)

enum Modality(val value: Int) {
  case Major    extends Modality(1)
  case Minor    extends Modality(0)
  case NoResult extends Modality(-1)
}

object AudioSection {
  given JsonDecoder[AudioSection] = DeriveJsonDecoder.gen[AudioSection]

  given JsonDecoder[Modality] = JsonDecoder[Int].map {
    case 1 => Modality.Major
    case 0 => Modality.Minor
    case _ => Modality.NoResult
  }
}
