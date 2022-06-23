package muse.domain.table

import java.time.Instant

final case class AppUser(
    id: String,
    createdAt: Instant
)
