package muse.utils

trait Clock[F[_]] {
  // Current time in seconds.
  def now: F[Long]
}
