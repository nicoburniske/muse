package muse.domain.session

import muse.domain.session.UserSession.instantString

import java.time.{Instant, ZoneId}

/**
 * @param sessionCookie
 *   the user's session cookie
 * @param id
 *   the user's spotify id
 * @param expiration
 *   the expiration of the access token
 * @param accessToken
 *   the spotify access token
 * @param refreshToken
 *   the spotify refresh token
 */
final case class UserSession(
    sessionCookie: String,
    id: String,
    expiration: Instant,
    accessToken: String,
    refreshToken: String
) {
  val conciseString =
    s"UserID: $id, Session Cookie: ${sessionCookie.take(10)}, Expiration: ${instantString(
      expiration)}, Access: ${accessToken.take(10)}, Refresh: ${refreshToken.take(10)}"
}

object UserSession {
  private val z                         = ZoneId.of("America/New_York")
  private def instantString(i: Instant) = i.atZone(z).toLocalTime.toString
}
