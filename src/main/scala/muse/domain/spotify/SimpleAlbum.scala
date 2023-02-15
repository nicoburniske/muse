package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class SimpleAlbum(
    albumGroup: Option[AlbumGroup],
    albumType: AlbumType,
    artists: Option[List[SimpleArtist]],
    availableMarkets: List[String],
    externalUrls: Option[Map[String, String]],
    href: String,
    id: String,
    images: List[Image],
    name: String,
    releaseDate: String,
    releaseDatePrecision: ReleaseDatePrecision,
    restrictions: Option[Restrictions],
    `type`: String,
    uri: String)

object SimpleAlbum {
  given JsonDecoder[SimpleAlbum] = DeriveJsonDecoder.gen[SimpleAlbum]
}
