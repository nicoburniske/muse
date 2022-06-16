package muse.utils

import sttp.monad.MonadError
import zio.{Task, ZIO}

object Givens {
  // TODO: change this to be IO. Don't want throwable as error bound.
  given zioMonadError: MonadError[Task] = new MonadError[Task] {
    // TODO fix whatever is going on here
    def ensure[T](f: Task[T], e: => Task[Unit]): Task[T]                                              = f
    def error[T](t: Throwable): Task[T]                                                               = ZIO.fail(t)
    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B]                                          = fa.flatMap(f)
    // TODO: fix this
    protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] = rt
    def map[A, B](fa: Task[A])(f: A => B): Task[B]                                                    = fa.map(f)
    def unit[T](t: T): Task[T]                                                                        = ZIO.succeed(t)
  }

}
