package muse.server

import zio.{Layer, Random, Ref, System, Task, UIO, URIO, ZEnvironment, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Request, Http, HttpError, Method, Response, *}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.http.Middleware.{bearerAuthZIO, csrfValidate}
import zhttp.http.*
import zio.json.*
import muse.domain.tables.AppUser
import muse.persist.DatabaseQueries
import muse.service.{RequestProcessor, SpotifyServiceLive, UserSessions}
import sttp.client3.SttpBackend
import muse.utils.Givens.given
import sttp.monad.MonadError

object Protected {
  val USER_PATH = "user"

  type ProtectedEndpointEnv = UserSessions & DatabaseQueries & SttpBackend[Task, Any]
  val endpoints =
    Http
      .collectZIO[RequestWithSession[AppUser]] {
        case RequestWithSession(appUser, Method.GET -> !! / USER_PATH / "reviews") =>
          getUserReviews(appUser).catchAll { e =>
            ZIO.succeed(HttpError.InternalServerError(cause = Some(e)).toResponse)
          }
      }
      .contramapZIO[ProtectedEndpointEnv, HttpError, (String, Request)] {
        case (token, req) => getSession(token, req)
      }
      .contramapZIO[ProtectedEndpointEnv, HttpError, Request] { req =>
        req.cookieValue("xsession") match {
          case Some(token) => ZIO.succeed(token.toString -> req)
          case _           => ZIO.fail(HttpError.Unauthorized("Missing Session Cookie"))
        }
      }
      .catchAll(error => Http.response(error.toResponse))

  // Introduce this after auth session middleware
  // @@ csrfValidate()

  private def getUserReviews(appUser: AppUser) = {
    for {
      sttpBackend <- ZIO.service[SttpBackend[Task, Any]]
      dbQueries   <- ZIO.service[DatabaseQueries]
      spotify      = SpotifyServiceLive(sttpBackend, appUser.accessToken, appUser.refreshToken)
      spotifyEnv   = ZEnvironment(spotify).add(dbQueries)
      reviews     <- RequestProcessor
                       .getUserReviews(appUser.id, RequestProcessor.ReviewOptions.UserAccessReviews)
                       .provideEnvironment(spotifyEnv)
    } yield Response.text(reviews.toJsonPretty)
  }

  case class RequestWithSession[A](session: A, request: Request)

  def getSession(token: String, req: Request): ZIO[UserSessions, HttpError, RequestWithSession[AppUser]] =
    for {
      maybeUser <- UserSessions.getUserSession(token)
      maybe     <- ZIO.fromOption(maybeUser).orElseFail("Invalid Session Cookie").either
      res       <- maybe match
                     case Left(value)  => ZIO.fail(HttpError.Unauthorized(value))
                     case Right(value) => ZIO.succeed(RequestWithSession(value, req))
    } yield res

}
