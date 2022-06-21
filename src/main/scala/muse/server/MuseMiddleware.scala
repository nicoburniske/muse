package muse.server

import muse.domain.session.{RequestWithSession, UserSession}
import muse.service.UserSessions
import muse.service.spotify.{SpotifyAPI, SpotifyAuthServiceLive, SpotifyService}
import muse.service.spotify.SpotifyAuthServiceLive.AuthEnv
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.{Http, HttpApp, HttpError, Request, Response}
import zio.{IO, UIO, ULayer, ZIO}
import zio.*

import java.time.Instant

object MuseMiddleware {
  case object Unauthorized extends Exception("Unauthorized")

  trait Auth[T] {
    type Unauthorized = Unauthorized.type

    def currentUser: IO[Unauthorized, T]
    def setUser(session: Option[T]): UIO[Unit]
  }

  val HttpLayer: ULayer[Auth[UserSession]] = ZLayer.scoped {
    FiberRef.make[Option[UserSession]](None).map { ref =>
      new Auth {
        def currentUser: IO[Unauthorized, UserSession] =
          ref.get.flatMap {
            case Some(v) => ZIO.succeed(v)
            case None    => ZIO.fail(Unauthorized)
          }

        def setUser(session: Option[UserSession]): UIO[Unit] = ref.set(session)
      }
    }
  }

  def userSessionAuth[R](
      app: Http[R, Throwable, Request, Response]
  ): Http[R & AuthEnv & Auth[UserSession] & SttpBackend[Task, Any], Throwable, Request, Response] =
    Http
      .fromFunctionZIO[Request] { (request: Request) =>
        request.cookieValue(COOKIE_KEY) match
          case None         => ZIO.fail(Unauthorized)
          case Some(cookie) =>
            for {
              session <- getSession(cookie.toString)
              _       <- ZIO.serviceWithZIO[Auth[UserSession]](_.setUser(Some(session)))
              spotify <- SpotifyService.live(session.accessToken)
              // How do I give spotify to the http app?
              asLayer  = ZLayer.succeed(spotify)
            } yield app.provideLayer(asLayer)
      }
      .flatten

  def getSession(cookie: String): ZIO[AuthEnv, Throwable, UserSession] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session   <- ZIO.fromOption(maybeUser).orElseFail(HttpError.Unauthorized("Invalid Session Cookie"))
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
