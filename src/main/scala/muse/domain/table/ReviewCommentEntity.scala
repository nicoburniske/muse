package muse.domain.table

import muse.domain.common.EntityType

final case class ReviewCommentEntity(commentId: Long, entityType: EntityType, entityId: String)
