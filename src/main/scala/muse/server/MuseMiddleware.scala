package muse.server

import muse.domain.session.UserSession
import muse.service.UserSessions
import muse.service.spotify.{SpotifyAPI, SpotifyAuthServiceLive, SpotifyService}
import muse.service.spotify.SpotifyAuthServiceLive.AuthEnv
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.{Http, HttpApp, HttpData, HttpError, Request, Response, Status}
import zio.{IO, UIO, ULayer, ZIO}
import zio.*

import java.time.Instant

object MuseMiddleware {
  case class Unauthorized(msg: Option[String])
      extends Exception(msg.map(m => s"Unauthorized: $m").getOrElse("Unauthorized")) {
    val http = Http.response(Response(Status.Unauthorized, data = HttpData.fromString(this.getMessage)))
  }

  trait Auth[T] {
    def currentUser: IO[Unauthorized, T]
    def setUser(session: Option[T]): UIO[Unit]
  }

  object Auth {
    def currentUser[T: Tag] = ZIO.serviceWithZIO[Auth[T]](_.currentUser)

    def setUser[T: Tag](t: Option[T]) = ZIO.serviceWithZIO[Auth[T]](_.setUser(t))
  }

  val FiberUserSession: ULayer[Auth[UserSession]] = ZLayer.scoped {
    FiberRef.make[Option[UserSession]](None).map { ref =>
      new Auth {
        def currentUser: IO[Unauthorized, UserSession] =
          ref.get.flatMap {
            case Some(v) => ZIO.succeed(v)
            case None    => ZIO.fail(Unauthorized(None))
          }

        def setUser(session: Option[UserSession]): UIO[Unit] = ref.set(session)
      }
    }
  }

  def UserSessionAuth[R](
      app: Http[R & SpotifyService, Throwable, Request, Response]
  ): Http[R & AuthEnv & Auth[UserSession] & SttpBackend[Task, Any], Throwable, Request, Response] =
    Http
      .fromFunctionZIO[Request] { (request: Request) =>
        request.cookieValue(COOKIE_KEY).orElse(request.authorization) match
          case None         =>
            ZIO.logInfo("Missing Cookie Header") *> ZIO.fail(Unauthorized(Some("Missing Auth")))
          case Some(cookie) =>
            for {
              session <- getSession(cookie.toString)
              _       <- Auth.setUser[UserSession](Some(session))
              _       <- ZIO.serviceWithZIO[Auth[UserSession]](_.setUser(Some(session)))
              spotify <- SpotifyService.live(session.accessToken)
              asLayer  = ZLayer.succeed(spotify)
            } yield app.provideSomeLayer[R, SpotifyService, Throwable](asLayer)
      }
      .flatten

  def getSession(cookie: String): ZIO[AuthEnv, Throwable, UserSession] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session   <- ZIO.fromOption(maybeUser).orElseFail(Unauthorized(Some("Invalid Auth")))
      session   <-
        if (session.expiration.isAfter(Instant.now()))
          ZIO.logInfo(s"Session Retrieved: ${session.conciseString}").as(session)
        else
          for {
            authData      <- SpotifyAuthServiceLive.requestNewAccessToken(session.refreshToken)
            newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
            newSession    <- UserSessions.updateUserSession(session.sessionCookie) {
                               _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                             }
            // These 'get' calls should be a-ok.
            _             <- ZIO.logInfo(s"Session Updated ${newSession.get.conciseString}")
          } yield newSession.get
    } yield session

}
