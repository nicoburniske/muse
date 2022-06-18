package muse.domain.tables

import java.util.UUID

final case class ReviewAccess(
    reviewId: UUID,
    userId: String,
    accessLevel: AccessLevel
)

enum AccessLevel:
  case Collaborator
  case Suggester
  case Viewer
