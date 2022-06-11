package domain.spotify
import zio.json.*

case class AuthData(
    @jsonField("token_type") tokenType: String,
    @jsonField("access_token") accessToken: String,
    @jsonField("refresh_token") refreshToken: String,
    @jsonField("scope") scope: String) 


object AuthData {
  given decoder: JsonDecoder[AuthData] = DeriveJsonDecoder.gen[AuthData]
}
