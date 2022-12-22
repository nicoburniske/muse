package muse.domain.spotify
import zio.json.*

@jsonMemberNames(SnakeCase)
final case class AuthCodeFlowData(
    tokenType: String,
    accessToken: String,
    refreshToken: String,
    scope: Option[String],
    expiresIn: Int)

object AuthCodeFlowData {
  given decoder: JsonDecoder[AuthCodeFlowData] = DeriveJsonDecoder.gen[AuthCodeFlowData]
}
