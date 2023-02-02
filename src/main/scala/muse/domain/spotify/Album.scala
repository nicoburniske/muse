package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class Album(
    albumGroup: Option[String],
    albumType: AlbumType,
    artists: List[Artist],
    availableMarkets: Option[List[String]],
    // copyrights: Option[List[Copyright]] = None,
    externalIds: Option[Map[String, String]],
    externalUrls: Map[String, String],
    genres: Option[List[String]],
    href: String,
    id: String,
    images: List[Image],
    label: Option[String],
    name: String,
    popularity: Option[Int],
    releaseDate: String,
    //                 releaseDatePrecision: ReleaseDatePrecision,
    //                 restrictions: Option[Restrictions] ,
    tracks: Option[Paging[AlbumTrack]],
    `type`: String,
    uri: String)
    extends Entity(id, EntityType.Album)

object Album {
  given decodeAlbumType: JsonDecoder[AlbumType] =
    JsonDecoder[String].map(AlbumType.fromString)

  given decodeAlbum: JsonDecoder[Album] = DeriveJsonDecoder.gen[Album]
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
