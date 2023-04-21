package muse.domain.spotify

import zio.json.*

@jsonMemberNames(SnakeCase)
final case class PublicUser(
    displayName: Option[String],
    externalUrls: Map[String, String],
    followers: Option[Followers],
    href: String,
    id: String,
    `type`: String,
    images: List[Image],
    uri: String
)

object PublicUser {
  given userDecoder: JsonDecoder[PublicUser] = DeriveJsonDecoder.gen[PublicUser]
}
