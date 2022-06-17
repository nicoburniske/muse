package muse.server

import zio.{Cause, Layer, Random, Ref, System, Task, UIO, URIO, ZEnvironment, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Http, HttpError, Method, Request, Response, *}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.http.Middleware.csrfValidate
import zhttp.http.*
import zio.json.*
import muse.domain.session.UserSession
import muse.persist.DatabaseQueries
import muse.service.spotify.SpotifyAuthServiceLive.AuthEnv
import muse.service.spotify.{SpotifyAuthServiceLive, SpotifyServiceLive}
import muse.service.{RequestProcessor, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import muse.utils.Givens.given
import sttp.monad.MonadError

import java.time.Instant

object Protected {
  val USER_PATH = "user"

  type ProtectedEndpointEnv = UserSessions & DatabaseQueries & SttpBackend[Task, Any]
  val endpoints =
    Http
      .collectZIO[RequestWithSession[UserSession]] {
        case RequestWithSession(appUser, Method.GET -> !! / USER_PATH / "reviews") =>
          getUserReviews(appUser)
      }
      .contramapZIO[ProtectedEndpointEnv & AuthEnv, Throwable, (String, Request)] {
        case (cookie, req) => getSession(cookie, req)
      }
      .contramapZIO[ProtectedEndpointEnv & AuthEnv, Throwable, Request] { req =>
        req.cookieValue("xsession") match {
          case Some(token) => ZIO.succeed(token.toString -> req)
          case _           => ZIO.fail(HttpError.Unauthorized("Missing Session Cookie"))
        }
      }
      .tapErrorZIO {
        case _: HttpError => ZIO.unit
        case e: Throwable => ZIO.logErrorCause("Internal Server error", Cause.fail(e))
      }
      .catchAll {
        case e: HttpError => Http.response(e.toResponse)
        case e: Throwable => Http.error(HttpError.InternalServerError(cause = Some(e)))
      }

  // TODO: Get CSRF working
  // @@ csrfValidate()

  private def getUserReviews(user: UserSession) = {
    for {
      sttpBackend        <- ZIO.service[SttpBackend[Task, Any]]
      dbQueries          <- ZIO.service[DatabaseQueries]
      spotify             = SpotifyServiceLive(sttpBackend, user.accessToken)
      spotifyEnv          = ZEnvironment(spotify).add(dbQueries)
      res                <- RequestProcessor
                              .getUserReviews(user.id, RequestProcessor.ReviewOptions.UserAccessReviews)
                              .provideEnvironment(spotifyEnv)
                              .timed
      (duration, reviews) = res
      _                  <- ZIO.logDebug(s" Fetching user reviews took ${duration.toMillis}ms")
    } yield Response.text(reviews.toJsonPretty)
  }

  final case class RequestWithSession[A](session: A, request: Request)

  def getSession(cookie: String, req: Request): ZIO[AuthEnv, Throwable, RequestWithSession[UserSession]] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session   <- ZIO.fromOption(maybeUser).orElseFail(HttpError.Unauthorized("Invalid Session Cookie"))
      res       <- if (session.expiration.isAfter(Instant.now())) for {
                     _ <- ZIO.log(s"Session Retrieved ${session.toString}")
                     r <- ZIO.succeed(RequestWithSession(session, req))
                   } yield r
                   else
                     for {
                       _             <- ZIO.log(s"Session Retrieved ${session.toString}")
                       authData      <- SpotifyAuthServiceLive.getAccessToken(session.refreshToken)
                       newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
                       newSession    <- UserSessions.updateUserSession(session.cookie) {
                                          _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                                        }
                       // This 'get' call should be a-ok.
                     } yield RequestWithSession(newSession.get, req)
    } yield res

}
