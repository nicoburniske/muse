package muse.domain.table

import muse.domain.common.Types.UserId

import java.util.UUID

final case class ReviewAccess(
    reviewId: UUID,
    userId: UserId,
    accessLevel: AccessLevel
)

enum AccessLevel:
  case Collaborator
  case Viewer
