package muse.service

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.service.spotify.SpotifyService
import zio.{FiberRef, IO, Tag, UIO, ULayer, ZIO, ZLayer}

trait RequestSession[T] {
  def get: IO[Unauthorized, T]
  def set(session: Option[T]): UIO[Unit]
}

object RequestSession {
  val userSessionLayer: ULayer[RequestSession[UserSession]] = ZLayer.scoped {
    FiberRef.make[Option[UserSession]](None).map(createSession)
  }

  val spotifySessionLayer: ULayer[RequestSession[SpotifyService]] = ZLayer.scoped {
    FiberRef.make[Option[SpotifyService]](None).map(createSession)
  }

  private def createSession[R: Tag](ref: FiberRef[Option[R]]) = {
    new RequestSession[R] {
      def get: IO[Unauthorized, R] =
        ref.get.flatMap {
          case Some(v) => ZIO.succeed(v)
          case None    => ZIO.fail(Unauthorized("Missing Websocket Auth"))
        }

      def set(session: Option[R]): UIO[Unit] = ref.set(session)
    }
  }

  def get[T: Tag] = ZIO.serviceWithZIO[RequestSession[T]](_.get)

  def set[T: Tag](t: Option[T]) = ZIO.serviceWithZIO[RequestSession[T]](_.set(t))
}
