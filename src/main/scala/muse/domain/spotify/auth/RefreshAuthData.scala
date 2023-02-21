package muse.domain.spotify.auth

import zio.json.*

import java.time.Instant
import java.time.temporal.ChronoUnit

@jsonMemberNames(SnakeCase)
final case class RefreshAuthData(
    accessToken: String,
    tokenType: String,
    // If no scopes requested, then no scopes will be received.
    scope: Option[String],
    expiresIn: Int)

object RefreshAuthData {
  given decoder: JsonDecoder[RefreshAuthData] = DeriveJsonDecoder.gen[RefreshAuthData]
}
