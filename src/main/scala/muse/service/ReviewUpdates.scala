package muse.service

import muse.domain.event.ReviewUpdate
import muse.domain.table.ReviewComment
import zio.stream.ZStream
import zio.{Hub, ZLayer}

import java.util.UUID

object ReviewUpdates {
  val hub = ZLayer.scoped(Hub.bounded[ReviewUpdate](128))
}
