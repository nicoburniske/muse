package muse.utils

import zio.*

/**
 * @tparam F
 *   The Effect Type
 * @tparam E
 *   The Error Type
 */
trait MonadError[F[_], E] {
  extension [A](a: A) def pure: F[A]
  extension [A, B](a: F[A]) def map(f: A => B): F[B]
  extension [A, B](a: F[A]) def flatMap(f: A => F[B]): F[B]
  extension [A](e: E) def raiseError: F[A]
  extension [A](a: F[A]) def handleErrorWith(f: E => F[A]): F[A]

  extension [A](a: F[A]) def isSuccess: F[Boolean] = a.map(_ => true).handleErrorWith(_ => false.pure)
  extension [A, B](a: F[A]) def as(b: B): F[B]     = a.map(_ => b)
  extension [A](a: F[A]) def unit: F[Unit]         = a.as(())
}
