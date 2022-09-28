package muse.server

import caliban.{CalibanError, GraphQLInterpreter, ZHttpAdapter}
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.middleware.HttpMiddleware
import zhttp.http.{Http, HttpApp, HttpData, HttpError, Middleware, Request, Response, Status}
import zio.*

import java.time.Instant

object MuseMiddleware {
  def checkAuthAddSession[R](app: Http[R & SpotifyService, Throwable, Request, Response]) = Http
    .fromFunctionZIO[Request] { request =>
      request
        .cookieValue(COOKIE_KEY)
        .orElse(request.authorization)
        .map(_.toString)
        .fold(
          ZIO.logInfo("Missing Auth") *> ZIO.fail(Unauthorized("Missing Auth"))
        )(auth =>
          for {
            session <- getSession(auth)
            _       <- RequestSession.set[UserSession](Some(session))
            layer   <- SpotifyService.getLayer
          } yield app.provideSomeLayer[R, SpotifyService, Throwable](layer))
    }
    .flatten

  def getSession(cookie: String): ZIO[SpotifyAuthService & UserSessions, Throwable, UserSession] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session   <- ZIO.fromOption(maybeUser).orElseFail(Unauthorized("Invalid Auth"))
      session   <-
        if (session.expiration.isAfter(Instant.now()))
          ZIO.logInfo(s"Session Retrieved: ${session.conciseString}").as(session)
        else
          for {
            authData      <- SpotifyAuthService.requestNewAccessToken(session.refreshToken)
            newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
            newSession    <- UserSessions.updateUserSession(session.sessionCookie) {
                               _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                             }
            // These 'get' calls should be a-ok.
            _             <- ZIO.logInfo(s"Session Updated ${newSession.get.conciseString}")
          } yield newSession.get
    } yield session

  val logErrors: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response]
    ): Http[R1, E1, Request, Response] =
      http
        .tapErrorZIO { case e: Throwable => ZIO.logErrorCause("Server Error", Cause.fail(e)) }
    //        .catchAll{ case e: Throwable => ZIO.logErrorCause("Server Error", Cause.fail(e)) }
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
   * It also adds a request ID to the logging context, so any further logging that occurs in the handler can be associated with
   * the same request.
   */
  val loggingMiddleware: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response]
    ): Http[R1, E1, Request, Response] =
      Http.fromOptionFunction[Request] { request =>
        Random.nextUUID.flatMap { requestId =>
          ZIO.logAnnotate("REQUEST-ID", requestId.toString) {
            http(request)
          }
        }
      }
  }
}
