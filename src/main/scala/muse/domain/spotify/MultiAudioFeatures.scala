package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonField, jsonMemberNames}

@jsonMemberNames(SnakeCase)
case class MultiAudioFeatures(audioFeatures: Vector[AudioFeatures])

object MultiAudioFeatures {
  given decoder: JsonDecoder[MultiAudioFeatures] = DeriveJsonDecoder.gen[MultiAudioFeatures]
}
