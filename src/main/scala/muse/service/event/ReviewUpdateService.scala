package muse.service.event

import muse.domain.event.ReviewUpdateEvent
import muse.domain.table.ReviewComment
import zio.stream.{UStream, ZStream}
import zio.*
import zio.schema.{DeriveSchema, Schema}

import java.util.UUID

trait ReviewUpdateService {
  def publish(update: ReviewUpdateEvent): UIO[Boolean]
  def subscribe(reviewIds: Set[UUID]): ZIO[Scope, Throwable, UStream[ReviewUpdateEvent]]
}

object ReviewUpdateService {
  val layer = ZLayer.fromFunction(ReviewUpdateServiceLive(_))

  val SUBJECT = "review-updates"

  def publish(update: ReviewUpdateEvent)   = ZIO.serviceWithZIO[ReviewUpdateService](_.publish(update))
  def subscribe(update: ReviewUpdateEvent) = ZIO.serviceWithZIO[ReviewUpdateService](_.subscribe(update))
}

final case class ReviewUpdateServiceLive(eventService: EventService) extends ReviewUpdateService {
  import ReviewUpdateService.*

  given Schema[ReviewUpdateEvent] = DeriveSchema.gen[ReviewUpdateEvent]

  override def publish(update: ReviewUpdateEvent) = eventService.publish(SUBJECT, update)

  override def subscribe(reviewIds: Set[UUID]) = for {
    stream <- eventService.subscribe(SUBJECT)
  } yield stream.filter(update => reviewIds.contains(update.reviewId)).catchAll(_ => ZStream.empty)
}
