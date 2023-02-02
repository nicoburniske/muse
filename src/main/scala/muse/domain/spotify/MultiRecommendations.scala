package muse.domain.spotify

import zio.json.*

final case class MultiRecommendations(tracks: Vector[Track], seeds: Vector[RecommendationSeed])

@jsonMemberNames(SnakeCase)
final case class RecommendationSeed(
    afterFilteringSize: Int,
    afterRelinkingSize: Int,
    href: String,
    id: String,
    initialPoolSize: Int,
    `type`: String)

object MultiRecommendations {
  given decoder: JsonDecoder[MultiRecommendations]   = DeriveJsonDecoder.gen[MultiRecommendations]
  given seedDecoder: JsonDecoder[RecommendationSeed] = DeriveJsonDecoder.gen[RecommendationSeed]
}
