package muse.domain.tables

import java.time.Instant

final case class AppUser(
    id: String,
    createdAt: Instant
)
