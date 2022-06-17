package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

final case class Artist(
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    // followers: Option[Followers],
    genres: Option[List[String]],
    href: String,
    id: String,
    // Is nullable because is references from different entities like Album.
    images: Option[List[Image]],
    name: String,
    popularity: Option[Int],
    @jsonField("type")
    `type`: String,
    uri: String
) extends Entity(id, EntityType.Artist)

object Artist {
  given decodeArtist: JsonDecoder[Artist] = DeriveJsonDecoder.gen[Artist]
}
