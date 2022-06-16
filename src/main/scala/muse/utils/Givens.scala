package muse.utils

import zio.{Task, ZIO}

object Givens {
  given zioMonadError[R, E]: MonadError[[A] =>> ZIO[R, E, A], E] = new MonadErrorZIO[R, E]
  given taskMonadError: MonadError[Task, Throwable]              = zioMonadError[Any, Throwable]
}

private class MonadErrorZIO[R, E]() extends MonadError[[A] =>> ZIO[R, E, A], E] {
  type F[A] = ZIO[R, E, A]
  override final def pure[A](a: A): F[A]                              = ZIO.succeed(a)
  override final def map[A, B](fa: F[A])(f: A => B): F[B]             = fa.map(f)
  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]      = fa.flatMap(f)
  override final def raiseError[A](e: E): F[A]                        = ZIO.fail(e)
  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] = fa.catchAll(f)
}
