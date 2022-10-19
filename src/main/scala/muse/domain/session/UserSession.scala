package muse.domain.session

import muse.domain.session.UserSession.instantString
import zio.json.{DeriveJsonCodec, JsonCodec}

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
    refreshToken: String
) {
  val conciseString =
    s"UserID: $userId, Session Cookie: ${sessionId.take(10)}, Expiration: ${instantString(expiration)}, Access: ${accessToken
      .take(10)}, Refresh: ${refreshToken.take(10)}"
}

object UserSession {
  given userSessionCodec: JsonCodec[UserSession] = DeriveJsonCodec.gen[UserSession]

  private val z = ZoneId.of("America/New_York")

  private def instantString(i: Instant) = i.atZone(z).toLocalTime.toString
}
