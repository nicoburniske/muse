package muse.domain.error

import muse.domain.common.EntityType

final case class InvalidEntity(entityId: String, entityType: EntityType)
    extends Exception(s"Invalid $entityType: $entityId")
