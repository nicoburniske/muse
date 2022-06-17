package muse.domain.session

import java.time.Instant

/**
 * @param cookie
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
    cookie: String,
    id: String,
    expiration: Instant,
    accessToken: String,
    refreshToken: String
)
