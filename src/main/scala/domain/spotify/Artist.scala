package domain.spotify

import zio.json.*

final case class Artist(
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    // followers: Option[Followers],
    genres: Option[List[String]],
    href: String,
    id: String,
    images: Option[List[Image]],
    name: String,
    popularity: Option[Int],
    `type`: String,
    uri: String
)

object Artist {
  given decodeArtist: JsonDecoder[Artist] = DeriveJsonDecoder.gen[Artist]
}
