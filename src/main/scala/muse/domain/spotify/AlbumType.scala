package muse.domain.spotify

import zio.json.JsonDecoder

enum AlbumType {
  case Album       extends AlbumType
  case Single      extends AlbumType
  case Compilation extends AlbumType
}

object AlbumType {
  given decodeAlbumType: JsonDecoder[AlbumType] =
    JsonDecoder[String].map(AlbumType.fromString)

  def fromString(s: String): AlbumType = s.toLowerCase match {
    case "album"       => Album
    case "single"      => Single
    case "compilation" => Compilation
  }
}
