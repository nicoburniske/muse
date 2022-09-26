package muse.server

import caliban.{CalibanError, GraphQLInterpreter, ZHttpAdapter}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.UserSessions
import muse.service.spotify.SpotifyAuthService.AuthEnv
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService, SpotifyServiceImpl}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.middleware.HttpMiddleware
import zhttp.http.{Http, HttpApp, HttpData, HttpError, Request, Response, Status}
import zio.*

import java.time.Instant

object MuseMiddleware {

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

  def userSessionAuth[R](
                          app: Http[R & SpotifyService, Throwable, Request, Response]
                        ): Http[
    R & AuthEnv & Auth[UserSession] & SttpBackend[Task, Any] & UserSessions,
    Throwable,
    Request,
    Response] =
    Http
      .fromFunctionZIO[Request] { (request: Request) =>
        request.cookieValue(COOKIE_KEY).orElse(request.authorization) match
          case None =>
            ZIO.logInfo("Missing Auth") *> ZIO.fail(Unauthorized("Missing Auth"))
          case Some(cookie) =>
            for {
              session <- getSession(cookie.toString)
              _ <- Auth.setUser[UserSession](Some(session))
              spotify <- SpotifyService.live(session.accessToken)
              asLayer = ZLayer.succeed(spotify)
            } yield app.provideSomeLayer[R, SpotifyService, Throwable](asLayer)
      }
      .flatten

  def makeSpotifyLayer = ZLayer.fromZIO {
    for {
      session <- Auth.currentUser[UserSession]
      spotify <- SpotifyService.live(session.accessToken)
    } yield spotify
  }

  def getSession(cookie: String): ZIO[AuthEnv & UserSessions, Throwable, UserSession] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session <- ZIO.fromOption(maybeUser).orElseFail(Unauthorized("Invalid Auth"))
      session <-
        if (session.expiration.isAfter(Instant.now()))
          ZIO.logInfo(s"Session Retrieved: ${session.conciseString}").as(session)
        else
          for {
            authData <- SpotifyAuthService.requestNewAccessToken(session.refreshToken)
            newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
            newSession <- UserSessions.updateUserSession(session.sessionCookie) {
              _.copy(accessToken = authData.accessToken, expiration = newExpiration)
            }
            // These 'get' calls should be a-ok.
            _ <- ZIO.logInfo(s"Session Updated ${newSession.get.conciseString}")
          } yield newSession.get
    } yield session

  val logErrors: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
                                                  http: Http[R1, E1, Request, Response]
                                                ): Http[R1, E1, Request, Response] =
      http
        .tapErrorZIO { case e: Throwable => ZIO.logErrorCause("Server Error", Cause.fail(e)) }
  }

  val catchUnauthorized: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
                                                  http: Http[R1, E1, Request, Response]
                                                ): Http[R1, E1, Request, Response] =
      http.catchSome { case u: Unauthorized => u.http }
  }

  /**
   * Logs the requests made to the server.
   *
   * It also adds a request ID to the logging context, so any further logging that occurs in the handler can
   * be associated with the same request.
   */
  val loggingMiddleware: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
                                                  http: Http[R1, E1, Request, Response]
                                                ): Http[R1, E1, Request, Response] =
    // TODO: where is the option?!
      Http.fromOptionFunction[Request] { request =>
        Random.nextUUID.flatMap { requestId =>
          ZIO.logAnnotate("REQUEST-ID", requestId.toString) {
            for {
              _ <- ZIO.logInfo(s"Request: $request")
              result <- http(request)
            } yield result
          }
        }
      }
  }
}
