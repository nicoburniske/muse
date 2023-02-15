package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

// Missing images
@jsonMemberNames(SnakeCase)
final case class PlaylistUser(
    displayName: Option[String],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    `type`: String,
    uri: String
)

object PlaylistUser {
  given JsonDecoder[PlaylistUser] = DeriveJsonDecoder.gen[PlaylistUser]
}
