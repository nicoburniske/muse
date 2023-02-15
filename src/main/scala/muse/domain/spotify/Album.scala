package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class Album(
    albumType: AlbumType,
    artists: List[SimpleArtist],
    availableMarkets: List[String],
    copyrights: List[Copyright],
    externalIds: ExternalIds,
    externalUrls: Map[String, String],
    genres: List[String],
    href: String,
    id: String,
    images: List[Image],
    label: String,
    name: String,
    popularity: Int,
    releaseDate: String,
    releaseDatePrecision: ReleaseDatePrecision,
    restrictions: Option[Restrictions],
    tracks: Paging[SimpleTrack],
    `type`: String,
    uri: String)

object Album {

  given decodeAlbum: JsonDecoder[Album] = DeriveJsonDecoder.gen[Album]
}
