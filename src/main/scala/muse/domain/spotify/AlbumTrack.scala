package muse.domain.spotify

import muse.domain.common.{Entity, EntityType}
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class AlbumTrack(
    artists: List[AlbumArtist],
    availableMarkets: Option[List[String]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalIds: Option[Map[String, String]],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    name: String,
    previewUrl: Option[String],
    trackNumber: Int,
    `type`: String,
    uri: String
)

@jsonMemberNames(SnakeCase)
final case class AlbumArtist(
    externalUrls: Map[String, String],
    href: String,
    id: String,
    name: String,
    `type`: String,
    uri: String)

object AlbumTrack {
  given decodeAlbumTrack: JsonDecoder[AlbumTrack]   = DeriveJsonDecoder.gen[AlbumTrack]
  given decodeAlbumArtist: JsonDecoder[AlbumArtist] = DeriveJsonDecoder.gen[AlbumArtist]
}
