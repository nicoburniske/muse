package muse.server

import zio.{Layer, Random, Ref, System, Task, URIO, ZEnvironment, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Http, Method, Request, Response, Scheme, URL, *}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.http.Middleware.{bearerAuthZIO, csrfValidate}
import zhttp.http.*
import zio.json.*
import muse.domain.tables.AppUser
import muse.persist.DatabaseQueries
import muse.service.{RequestProcessor, SpotifyService}
import sttp.client3.SttpBackend
import muse.utils.Givens.given

object Protected {
  val USER_PATH = "user"

  type ProtectedEndpointEnv = SignedIn & DatabaseQueries & SttpBackend[Task, Any]
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
      spotify      = SpotifyService(sttpBackend, appUser.accessToken, appUser.refreshToken)
      spotifyEnv   = ZEnvironment(spotify).add(dbQueries)
      reviews     <- RequestProcessor.getUserReviews(appUser.id, false).provideEnvironment(spotifyEnv)
    } yield Response.text(reviews.toJson)
  }

  type SignedIn = Ref[Map[String, AppUser]]
  case class RequestWithSession[A](session: A, request: Request)

  def getSession(token: String, req: Request): ZIO[SignedIn, HttpError, RequestWithSession[AppUser]] =
    getUserSessionFromToken(token).mapBoth(HttpError.Unauthorized(_), RequestWithSession(_, req))

  // TODO: check for expiration
  def getUserSessionFromToken(token: String): ZIO[SignedIn, String, AppUser] = for {
    usersRef <- ZIO.service[SignedIn]
    users    <- usersRef.get
    res      <- ZIO.fromOption(users.get(token)).orElseFail("Invalid Session Cookie")
  } yield res

}
