package muse.domain.table

import muse.domain.common.Types.{RefreshToken, SessionId, UserId}

import java.time.Instant

final case class UserSession(sessionId: SessionId, refreshToken: RefreshToken, userId: UserId, createdAt: Instant)
