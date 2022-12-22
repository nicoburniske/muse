package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class User(
    displayName: Option[String],
    externalUrls: Map[String, String],
    href: String,
    id: String,
    `type`: String,
    uri: String,
    images: Option[List[Image]],
    followers: Option[Followers]
)

object User {
  given userDecoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]
}
