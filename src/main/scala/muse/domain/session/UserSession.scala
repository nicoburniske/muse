package muse.domain.session

import muse.domain.common.Types.{AccessToken, SessionId, UserId, RefreshToken}
import muse.service.spotify.SpotifyService

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
    sessionId: SessionId,
    userId: UserId,
    spotifyData: SpotifyData
)

final case class SpotifyData(
    accessToken: AccessToken,
    refreshToken: RefreshToken,
    expiration: Instant
)
