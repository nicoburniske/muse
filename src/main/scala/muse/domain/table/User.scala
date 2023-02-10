package muse.domain.table

import java.time.Instant

final case class User(
    userId: String,
    createdAt: Instant
)
