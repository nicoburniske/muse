package muse.domain.spotify.auth

import zio.http.model.Status
import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonMemberNames}

final case class SpotifyAuthError(status: Status, content: AuthErrorContent) extends Exception
trait AuthErrorContent

/**
 * Error response from Spotify Auth API.
 */
@jsonMemberNames(SnakeCase)
final case class SpotifyAuthErrorResponse(error: String, errorDescription: String) extends AuthErrorContent

object SpotifyAuthErrorResponse {

  // Happens when the refresh token is revoked.
  val revoked = SpotifyAuthErrorResponse("invalid_grant", "Refresh token revoked")

  given JsonDecoder[SpotifyAuthErrorResponse] = DeriveJsonDecoder.gen[SpotifyAuthErrorResponse]
}

/**
 * Deserialization error. AKA an unexpected/unhandled response from Spotify.
 */
final case class SpotifyAuthDeserializationError(message: String, error: String, body: String) extends AuthErrorContent

object SpotifyAuthDeserializationError {
  given JsonDecoder[SpotifyAuthDeserializationError] = DeriveJsonDecoder.gen[SpotifyAuthDeserializationError]
}
