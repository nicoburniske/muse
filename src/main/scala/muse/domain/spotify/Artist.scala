package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class Artist(
    externalUrls: Map[String, String],
    followers: Followers,
    genres: List[String],
    href: String,
    id: String,
    images: List[Image],
    name: String,
    popularity: Int,
    `type`: String,
    uri: String
)

object Artist {
  given decodeArtist: JsonDecoder[Artist] = DeriveJsonDecoder.gen[Artist]

}
