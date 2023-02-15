package muse.utils

trait Ref[F[_], A] {
  def get: F[A]
  def set(a: A): F[Unit]
}
