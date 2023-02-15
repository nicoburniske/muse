package muse.utils

trait Clock[F[_]] {
  // Current time in milliseconds.
  def now: F[Long]
}
