package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonField, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class AudioFeatures(
    acousticness: Double,
    analysisUrl: String,
    danceability: Double,
    durationMs: Int,
    energy: Double,
    id: String,
    instrumentalness: Double,
    key: Int,
    liveness: Double,
    loudness: Double,
    mode: Int,
    speechiness: Double,
    tempo: Double,
    timeSignature: Int,
    trackHref: String,
    `type`: String,
    uri: String,
    valence: Double
)

object AudioFeatures {
  given decoder: JsonDecoder[AudioFeatures] = DeriveJsonDecoder.gen[AudioFeatures]
}
