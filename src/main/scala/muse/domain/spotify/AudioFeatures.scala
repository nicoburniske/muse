package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, jsonField}


case class AudioFeatures(
                          acousticness: Double,
                          @jsonField("analysis_url")
                          analysisUrl: String,
                          danceability: Double,
                          @jsonField("duration_ms")
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
    @jsonField("time_signature")
                          timeSignature: Int,
                          @jsonField("track_href")
                          trackHref: String,
                          `type`: String,
                          uri: String,
                          valence: Double
                        )

object AudioFeatures {
  given decoder: JsonDecoder[AudioFeatures] = DeriveJsonDecoder.gen[AudioFeatures]
}
