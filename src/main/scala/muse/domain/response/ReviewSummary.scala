package muse.domain.response

import java.time.Instant
import java.util.UUID
import zio.json.*

import muse.domain.common.EntityType
import muse.domain.spotify.Image
import muse.domain.tables.Review

final case class ReviewSummary(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    // Spotify entity data.
    entityType: EntityType,
    entityId: String,
    entityName: String,
    imageUrl: List[String]
)

object ReviewSummary {
  given reviewSummaryDecoder: JsonDecoder[ReviewSummary] = DeriveJsonDecoder.gen[ReviewSummary]
  given reviewSummaryEncoder: JsonEncoder[ReviewSummary] = DeriveJsonEncoder.gen[ReviewSummary]

  def fromReview(r: Review, entityName: String, images: List[Image]): ReviewSummary = {
    val imageUrls = images.flatMap(_.url)
    ReviewSummary(
      r.id,
      r.createdAt,
      r.creatorId,
      r.reviewName,
      r.isPublic,
      r.entityType,
      r.entityId,
      entityName,
      imageUrls
    )
  }
}
