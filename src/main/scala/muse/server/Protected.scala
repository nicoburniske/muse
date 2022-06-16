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
import muse.service.spotify.SpotifyServiceLive
import muse.service.{RequestProcessor, UserSessions}
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
          getUserReviews(appUser)
      }
      .contramapZIO[ProtectedEndpointEnv, Throwable, (String, Request)] {
        case (token, req) => getSession(token, req)
      }
      .contramapZIO[ProtectedEndpointEnv, Throwable, Request] { req =>
        req.cookieValue("xsession") match {
          case Some(token) => ZIO.succeed(token.toString -> req)
          case _           => ZIO.fail(HttpError.Unauthorized("Missing Session Cookie"))
        }
      }
      .catchAll {
        case e: HttpError => Http.response(e.toResponse)
        case e: Throwable => Http.error(HttpError.InternalServerError(cause = Some(e)))
      }

  // Introduce this after auth session middleware
  // @@ csrfValidate()

  private def getUserReviews(appUser: AppUser) = {
    for {
      sttpBackend        <- ZIO.service[SttpBackend[Task, Any]]
      dbQueries          <- ZIO.service[DatabaseQueries]
      spotify             = SpotifyServiceLive(sttpBackend, appUser.accessToken)
      spotifyEnv          = ZEnvironment(spotify).add(dbQueries)
      res                <- RequestProcessor
                              .getUserReviews(appUser.id, RequestProcessor.ReviewOptions.UserAccessReviews)
                              .provideEnvironment(spotifyEnv)
                              .timed
      (duration, reviews) = res
      _                  <- printLine(s"Took ${duration.toMillis}ms")
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
