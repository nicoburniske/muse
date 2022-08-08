package muse.domain.spotify

import zio.json.*

import java.time.Instant
import java.time.temporal.ChronoUnit

final case class RefreshAuthData(
    @jsonField("access_token") accessToken: String,
    @jsonField("token_type") tokenType: String,
    // If no scopes requested, then no scopes will be received.
    scope: Option[String],
    @jsonField("expires_in") expiresIn: Int)

object RefreshAuthData {
  given decoder: JsonDecoder[RefreshAuthData] = DeriveJsonDecoder.gen[RefreshAuthData]
}
