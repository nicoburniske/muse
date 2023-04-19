package muse.domain.mutate

import java.util.UUID

import caliban.schema.{ArgBuilder, Schema}

// If position is None, then the child review is appended to the end of the list.
final case class LinkReviews(parentReviewId: UUID, childReviewId: UUID, linkIndex: Option[Int]) 
final case class LinkReviewsInput(input: LinkReviews)
