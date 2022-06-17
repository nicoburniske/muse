package muse.server

import muse.domain.create.{CreateComment, CreateReview}
import muse.domain.session.{RequestWithSession, UserSession}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyAuthServiceLive.AuthEnv
import muse.service.spotify.{SpotifyAPI, SpotifyAuthServiceLive}
import muse.service.{RequestProcessor, UserSessions}
import muse.utils.Givens.given
import muse.utils.Utils
import sttp.client3.SttpBackend
import sttp.monad.MonadError
import zhttp.http.Middleware.csrfValidate
import zhttp.http.*
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.Console.printLine
import zio.json.*
import zio.{Cause, Layer, Random, Ref, System, Task, UIO, URIO, ZEnvironment, ZIO, ZIOAppDefault, ZLayer}

import java.time.Instant

object Protected {
  val USER_PATH = "user"
  type ProtectedEndpointEnv = UserSessions & DatabaseQueries & SttpBackend[Task, Any]

  val endpoints =
    Http
      .collectZIO[RequestWithSession[UserSession]] {
        case RequestWithSession(userSession, Method.POST -> !! / USER_PATH / "logout")                   =>
          logoutUser(userSession)
        case RequestWithSession(userSession, Method.GET -> !! / USER_PATH / "reviews")                   =>
          getUserReviews(userSession)
        case RequestWithSession(userSession, req @ Method.POST -> !! / USER_PATH / "review")             =>
          createReview(userSession, req)
        case RequestWithSession(userSession, req @ Method.POST -> !! / USER_PATH / "review" / "comment") =>
          createComment(userSession, req)
      }
      .contramapZIO[ProtectedEndpointEnv & AuthEnv, Throwable, (String, Request)] {
        case (cookie, req) => getSession(cookie, req)
      }
      .contramapZIO[ProtectedEndpointEnv & AuthEnv, Throwable, Request] { req =>
        req.cookieValue(COOKIE_KEY) match {
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
      res                <- RequestProcessor.getUserReviews(user, RequestProcessor.ReviewOptions.UserAccessReviews).timed
      (duration, reviews) = res
      _                  <- ZIO.logDebug(s" Fetching user reviews took ${duration.toMillis}ms")
    } yield Response.text(reviews.toJsonPretty)
  }

  private def logoutUser(user: UserSession) = for {
    _ <- UserSessions.deleteUserSession(user.sessionCookie)
  } yield Response.ok

  private def createReview(user: UserSession, req: Request) = for {
    body   <- req.bodyAsString
    review <- deserializeBodyOrFail[CreateReview](body)
    _      <- RequestProcessor.createReview(user, review)
  } yield Response.ok

  private def createComment(user: UserSession, req: Request) = for {
    body    <- req.bodyAsString
    comment <- deserializeBodyOrFail[CreateComment](body)
    _       <- RequestProcessor.createReviewComment(user, comment)
  } yield Response.ok

  private def deserializeBodyOrFail[T](body: String)(using decoder: JsonDecoder[T]) =
    body.fromJson[T] match {
      case Left(error) =>
        val message = s"Invalid Request Body: $error"
        ZIO.fail(HttpError.BadRequest(message))
      case Right(data) => ZIO.succeed(data)
    }

  def getSession(cookie: String, req: Request): ZIO[AuthEnv, Throwable, RequestWithSession[UserSession]] =
    for {
      maybeUser <- UserSessions.getUserSession(cookie)
      session   <- ZIO.fromOption(maybeUser).orElseFail(HttpError.Unauthorized("Invalid Session Cookie"))
      res       <- if (session.expiration.isAfter(Instant.now())) for {
                     _ <- ZIO.logInfo(s"Session Retrieved: ${session.conciseString}")
                     r <- ZIO.succeed(RequestWithSession(session, req))
                   } yield r
                   else
                     for {
                       _             <- ZIO.log(s"Session Retrieved ${session.toString}")
                       authData      <- SpotifyAuthServiceLive.getAccessToken(session.refreshToken)
                       newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
                       newSession    <- UserSessions.updateUserSession(session.sessionCookie) {
                                          _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                                        }
                       // This 'get' call should be a-ok.
                     } yield RequestWithSession(newSession.get, req)
    } yield res

}
