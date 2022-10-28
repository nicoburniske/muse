package muse.domain.session

import muse.domain.session.UserSession.instantString
import zio.json.{DeriveJsonCodec, JsonCodec}
import muse.service.spotify.SpotifyService

import java.time.{Instant, ZoneId}

/**
 * @param sessionId
 *   the user's session cookie
 * @param userId
 *   the user's spotify id
 * @param expiration
 *   the expiration of the access token
 * @param accessToken
 *   the spotify access token
 * @param refreshToken
 *   the spotify refresh token
 */
final case class UserSession(
    sessionId: String,
    userId: String,
    expiration: Instant,
    accessToken: String,
    refreshToken: String,
    spotifyService: SpotifyService
)

object UserSession {
  private val z = ZoneId.of("America/New_York")
  private def instantString(i: Instant) = i.atZone(z).toLocalTime.toString
}
