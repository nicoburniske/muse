package muse.domain.mutate

import muse.domain.common.EntityType
import zio.json.*

final case class CreateReview(name: String, isPublic: Boolean)
