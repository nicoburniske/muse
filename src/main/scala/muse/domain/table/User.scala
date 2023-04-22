package muse.domain.table

import muse.domain.common.Types.UserId

import java.time.Instant

final case class User(
    userId: UserId,
    createdAt: Instant
)
