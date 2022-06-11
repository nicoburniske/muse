package service

import zio.*
import zio.Task
import zio.Console.*
import sttp.client3.*
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.monad.MonadError

object ExampleSpotify extends ZIOAppDefault {
  val accessToken  = ""
  val refreshToken = ""

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

  override def run             = {
    val program = for {
      backend  <- AsyncHttpClientZioBackend()
      spotify   = Spotify[Task](backend, accessToken, refreshToken)
      response <- spotify.getAllPlaylistTracks("4FXSFL5xzbK9iSS1vpp2zd")
      _        <- printLine(response)
    } yield ()
    program
  }

}
