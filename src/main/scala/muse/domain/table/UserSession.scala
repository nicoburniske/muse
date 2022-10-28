package muse.domain.table

import java.time.Instant

case class UserSession(sessionId: String, refreshToken: String, userId: String, createdAt: Instant)
