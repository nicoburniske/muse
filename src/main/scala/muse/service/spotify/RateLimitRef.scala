package muse.service.spotify

import zio.{Ref, ZLayer, Clock, Console}
import java.time.temporal.ChronoUnit

object RateLimitRef {
  def layer = ZLayer.fromZIO {
    for {
      ref <- Ref.make(Option.empty[Long])
    } yield ref
  }
}
