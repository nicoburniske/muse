package muse.utils

import zio.ZIO

import java.time.Instant
import java.time.temporal.ChronoUnit

object Utils {
  val DEFAULT_EXPIRATION_PADDING = 30
  // TODO: Should this go in package object?

  /**
   * @param expiresIn
   *   Number of seconds from now.
   * @param padding
   *   Amount of padding expiration should be created with. Must be greater than expiresIn
   * @return
   *   The instant when something has expired
   */
  def getExpirationInstant(expiresIn: Int, padding: Int = DEFAULT_EXPIRATION_PADDING) =
    ZIO.succeed(Instant.now().plus(expiresIn - padding, ChronoUnit.SECONDS))
}
