package domain.create

final case class CreateReview(creatorId: String, isPublic: Boolean, entityType: Int, entityId: String)
