package muse.server

import muse.domain.create.{CreateComment, CreateReview}
import muse.domain.session.{RequestWithSession, UserSession}
import muse.domain.tables.{Review, ReviewComment}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyAuthServiceLive.AuthEnv
import muse.service.spotify.{SpotifyAPI, SpotifyAuthServiceLive}
import muse.service.{RequestProcessor, UserSessions}
import muse.utils.Givens.given
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.Middleware.csrfValidate
import zhttp.http.*
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.Console.printLine
import zio.json.*
import zio.{Cause, IO, Layer, Random, Ref, System, Task, UIO, URIO, ZEnvironment, ZIO, ZIOAppDefault, ZLayer}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID

object Protected {
  val USER_PATH = "user"
  type ProtectedEndpointEnv = UserSessions & DatabaseQueries & SttpBackend[Task, Any]

  val endpoints =
    Http
      .collectZIO[RequestWithSession[UserSession]] {
        case RequestWithSession(session, Method.GET -> !! / USER_PATH / "me")                        =>
          RequestProcessor.getUserInfo(session.accessToken).map(user => Response.text(user.toJson))
        case RequestWithSession(session, Method.GET -> !! / USER_PATH / "reviews")                   =>
          getUserReviews(session)
        case RequestWithSession(session, Method.GET -> !! / USER_PATH / "review" / id)               =>
          RequestProcessor.getDetailedReview(session, id).map(r => Response.text(r))
        case RequestWithSession(session, req @ Method.POST -> !! / USER_PATH / "review")             =>
          createReview(session, req)
        case RequestWithSession(session, req @ Method.POST -> !! / USER_PATH / "review" / "comment") =>
          createComment(session, req)
        case RequestWithSession(userSession, Method.POST -> !! / USER_PATH / "logout")               =>
          // TODO: need to clear cookie in response?
          UserSessions.deleteUserSession(userSession.sessionCookie).as(Response.ok)
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

  private def deserializeBodyOrFail[T](body: String)(using decoder: JsonDecoder[T]) = body
    .fromJson[T]
    .fold(error => ZIO.fail(HttpError.BadRequest(s"Invalid Request Body: $error")), ZIO.succeed(_))

  def getSession(cookie: String, req: Request): ZIO[AuthEnv, Throwable, RequestWithSession[UserSession]] =
    for {
      maybeUser   <- UserSessions.getUserSession(cookie)
      session     <- ZIO.fromOption(maybeUser).orElseFail(HttpError.Unauthorized("Invalid Session Cookie"))
      withSession <-
        if (session.expiration.isAfter(Instant.now()))
          ZIO.logInfo(s"Session Retrieved: ${session.conciseString}").as(RequestWithSession(session, req))
        else
          for {
            authData      <- SpotifyAuthServiceLive.requestNewAccessToken(session.refreshToken)
            newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
            newSession    <- UserSessions.updateUserSession(session.sessionCookie) {
                               _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                             }
            // These 'get' calls should be a-ok.
            _             <- ZIO.logInfo(s"Session Updated ${newSession.get.conciseString}")
          } yield RequestWithSession(newSession.get, req)
    } yield withSession

}
