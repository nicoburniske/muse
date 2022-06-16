package muse.utils

import zio.*

/**
 * @tparam F
 *   The Effect Type
 * @tparam E
 *   The Error Type
 */
trait MonadError[F[_], E] {
  def pure[A](x: A): F[A]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def raiseError[A](e: E): F[A]
  def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A]
}
