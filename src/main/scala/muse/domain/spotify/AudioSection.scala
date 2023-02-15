package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class AudioSection(
    start: Double,
    duration: Double,
    confidence: Confidence,
    loudness: Double,
    tempo: Int,
    tempoConfidence: Confidence,
    key: Double,
    keyConfidence: Confidence,
    mode: Modality,
    modeConfidence: Confidence,
    timeSignature: Double,
    timeSignatureConfidence: Confidence
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
