package muse.service

import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import zio.{FiberRef, IO, Tag, UIO, ULayer, ZIO, ZLayer}

trait RequestSession[T] {
  def get: IO[Unauthorized, T]
  def set(session: Option[T]): UIO[Unit]
}

object RequestSession {
  val layer: ULayer[RequestSession[UserSession]] = ZLayer.scoped {
    FiberRef.make[Option[UserSession]](None).map { ref =>
      new RequestSession {
        def get: IO[Unauthorized, UserSession] =
          ref.get.flatMap {
            case Some(v) => ZIO.succeed(v)
            case None    => ZIO.fail(Unauthorized(None))
          }

        def set(session: Option[UserSession]): UIO[Unit] = ref.set(session)
      }
    }
  }

  def get[T: Tag] = ZIO.serviceWithZIO[RequestSession[T]](_.get)

  def set[T: Tag](t: Option[T]) = ZIO.serviceWithZIO[RequestSession[T]](_.set(t))
}
