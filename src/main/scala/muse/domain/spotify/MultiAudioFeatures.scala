package muse.domain.spotify

import zio.json.{JsonDecoder, jsonField, DeriveJsonDecoder}

case class MultiAudioFeatures(@jsonField("audio_features") audioFeatures: Vector[AudioFeatures])

object MultiAudioFeatures {
  given decoder: JsonDecoder[MultiAudioFeatures] = DeriveJsonDecoder.gen[MultiAudioFeatures]
}
