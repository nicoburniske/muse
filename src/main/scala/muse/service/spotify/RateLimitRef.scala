package muse.service.spotify

import zio.{Clock, Console, Ref, ZLayer}

import java.time.temporal.ChronoUnit

object RateLimitRef {
  def layer = ZLayer.fromZIO {
    for {
      ref <- Ref.make(Option.empty[Long])
    } yield ref
  }
}
