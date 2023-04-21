package muse.domain.session

import muse.service.spotify.SpotifyService
import nl.vroste.rezilience.Bulkhead

import java.time.Instant

/**
 * @param sessionId
 *   the user's session cookie
 * @param userId
 *   the user's spotify id
 * @param accessToken
 *   the spotify access token
 */
final case class UserSession(
    sessionId: String,
    userId: String,
    accessToken: String
)
