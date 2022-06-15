package muse.domain.spotify

import zio.json.*

final case class User(
    @jsonField("display_name")
    displayName: Option[String],
    @jsonField("external_urls")
    externalUrls: Map[String, String],
    href: String,
    id: String,
    @jsonField("type")
    `type`: String,
    uri: String
)

object User {
  given userDecoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]
}
