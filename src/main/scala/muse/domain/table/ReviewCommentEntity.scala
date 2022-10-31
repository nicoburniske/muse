package muse.domain.table

import muse.domain.common.EntityType

case class ReviewCommentEntity(commentId: Int, entityType: EntityType, entityId: String)
