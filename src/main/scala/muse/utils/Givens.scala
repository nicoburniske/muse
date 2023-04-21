package muse.utils

import sttp.monad.MonadError as SttpMonadError
import zio.{Task, UIO, ZIO, Ref as ZRef}

import java.time.temporal.ChronoUnit

object Givens {
  given zioMonadError[R, E]: MonadError[[A] =>> ZIO[R, E, A], E] = new MonadErrorZIO[R, E]
  given taskMonadError: MonadError[Task, Throwable]              = zioMonadError[Any, Throwable]

  given zioClock: Clock[Task] = new Clock[Task]:
    override def now = zio.Clock.currentTime(ChronoUnit.SECONDS)

  def zioRef[A](zref: ZRef[A]): Ref[Task, A] = new Ref[Task, A] {
    def get: Task[A]          = zref.get
    def set(a: A): Task[Unit] = zref.set(a)
  }
}

private class MonadErrorZIO[R, E] extends MonadError[[A] =>> ZIO[R, E, A], E] {
  type F[A] = ZIO[R, E, A]

  extension [A](a: A) def pure: F[A]                             = ZIO.succeed(a)
  extension [A, B](a: F[A]) def map(f: A => B): F[B]             = a.map(f)
  extension [A, B](a: F[A]) def flatMap(f: A => F[B]): F[B]      = a.flatMap(f)
  extension [A](e: E) def raiseError: F[A]                       = ZIO.fail(e)
  extension [A](a: F[A]) def handleErrorWith(f: E => F[A]): F[A] = a.catchAll(f)

}
