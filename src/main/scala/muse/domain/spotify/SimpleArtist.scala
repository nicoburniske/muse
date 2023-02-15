package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class SimpleArtist(
    externalUrls: Map[String, String],
    href: String,
    id: String,
    name: String,
    `type`: String,
    uri: String)

object SimpleArtist {
  given decodeAlbumArtist: JsonDecoder[SimpleArtist] = DeriveJsonDecoder.gen[SimpleArtist]
}
