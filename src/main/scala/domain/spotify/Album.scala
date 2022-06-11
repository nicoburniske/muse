package domain.spotify

import zio.json.*

final case class Album(
    @jsonField("album_group")
    albumGroup: Option[String],
    @jsonField("album_type")
    albumType: AlbumType,
    artists: List[Artist],
    @jsonField("available_markets")
    availableMarkets: List[String],
    // copyrights: Option[List[Copyright]] = None,
    @jsonField("external_ids")
    externalIds: Option[Map[String, String]],
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    genres: Option[List[String]],
    href: String,
    id: String,
    images: List[Image],
    label: Option[String],
    name: String,
    popularity: Option[Int],
    @jsonField("release_date")
    releaseDate: String,
    //                 releaseDatePrecision: ReleaseDatePrecision,
    //                 restrictions: Option[Restrictions] ,
    tracks: Option[Paging[Track]],
    @jsonField("type")
    objectType: String,
    uri: String)

object Album {
  given decodeAlbumType: JsonDecoder[AlbumType] =
    JsonDecoder[String].map(AlbumType.fromString)
  given decodeAlbum: JsonDecoder[Album]         = DeriveJsonDecoder.gen[Album]
}

object AlbumType {
  def fromString(s: String): AlbumType = s.toLowerCase match {
    case "album"       => AlbumT
    case "single"      => SingleT
    case "compilation" => CompilationT
  }
}

enum AlbumType {
  case AlbumT       extends AlbumType
  case SingleT      extends AlbumType
  case CompilationT extends AlbumType
}
