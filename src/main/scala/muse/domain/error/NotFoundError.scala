package muse.domain.error

import muse.domain.common.EntityType

case class NotFoundError(entityId: String, entityType: EntityType)
    extends Throwable(s"${entityType.toString} $entityId not found")
