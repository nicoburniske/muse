package muse.domain.table

import java.time.Instant

final case class User(
    id: String,
    createdAt: Instant
)
