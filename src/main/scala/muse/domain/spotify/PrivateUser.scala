package muse.domain.spotify
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class PrivateUser(
    country: Option[String],
    displayName: Option[String],
    email: Option[String],
    externalUrls: Map[String, String],
    followers: Followers,
    href: String,
    id: String,
    images: List[Image],
    product: Option[String],
    uri: String
)

object PrivateUser {
  given JsonDecoder[PrivateUser] = DeriveJsonDecoder.gen[PrivateUser]
}
