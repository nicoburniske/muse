package muse.utils

import zio.ZIO

import java.time.Instant
import java.time.temporal.ChronoUnit

object Utils {
  val DEFAULT_EXPIRATION_PADDING = 30

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

  def addTimeLog[R, E, A](message: String)(z: ZIO[R, E, A]): ZIO[R, E, A] =
    z.timed.flatMap { case (d, r) => ZIO.logInfo(s"$message in ${d.toMillis}ms").as(r) }
}
