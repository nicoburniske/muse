package muse.domain.spotify

import zio.json.JsonDecoder

// Only appears on Artist Albums Request.
enum AlbumGroup {
  case Album       extends AlbumGroup
  case Single      extends AlbumGroup
  case Compilation extends AlbumGroup
  case AppearsOn   extends AlbumGroup
}

object AlbumGroup {
  given JsonDecoder[AlbumGroup] = JsonDecoder[String].map(AlbumGroup.fromString)

  def fromString(s: String): AlbumGroup = s.toLowerCase match {
    case "album"       => Album
    case "single"      => Single
    case "compilation" => Compilation
    case "appears_on"  => AppearsOn
  }
}
