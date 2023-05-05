package muse.server

import caliban.Value.StringValue
import caliban.interop.tapir.{StreamTransformer, WebSocketHooks, WebSocketInterpreter}
import caliban.*
import caliban.interop.tapir.TapirAdapter.TapirResponse
import io.netty.handler.codec.http.HttpHeaderNames
import muse.domain.common.Types.SessionId
import muse.domain.error.{MuseError, RateLimited, Unauthorized}
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.cache.RedisService
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService, SpotifyServiceLive}
import muse.service.{RequestSession, UserSessionService}
import muse.utils.Utils
import nl.vroste.rezilience.Bulkhead
import nl.vroste.rezilience.Bulkhead.{BulkheadError, BulkheadException, WrappedError}
import sttp.client3.SttpBackend
import sttp.tapir.model.ServerRequest
import sttp.model.StatusCode
import zio.*
import zio.http.Http.Route
import zio.http.*
import zio.http.Header.Authorization
import zio.redis.Redis
import zio.stream.ZStream

import java.time.Instant

object MuseMiddleware {

  val InjectSessionAndRateLimit = HttpAppMiddleware.customAuthProvidingZIO(
    getSessionZioHttp,
    Headers.empty,
    Status.Unauthorized
  )

  def getSession(maybeSessionId: Option[String]): ZIO[RedisService with UserSessionService, Throwable, UserSession] = {
    for {
      session       <- maybeSessionId match
                         case None            => ZIO.fail(Unauthorized("Missing Session ID."))
                         case Some(sessionId) =>
                           UserSessionService
                             .getUserSession(SessionId(sessionId))
                             .tapErrorCause { c => ZIO.logErrorCause("Failed to get user session", c) }
      isRateLimited <- RedisService
                         .rateLimited(session.userId).retry(Schedule.recurs(3) && Schedule.spaced(15.millis).jittered)
                         .tapErrorCause { c => ZIO.logErrorCause("Failed to check rate limit", c) }
      _             <- {
        ZIO.logError(s"Rate Limited ${session.userId}") *> ZIO.fail(RateLimited)
      }.when(isRateLimited)
    } yield session
  }

  def getSessionZioHttp(headers: Headers) = getSession(extractRequestAuth(headers))
    .mapBoth(
      {
        case u: Unauthorized => u.response
        case RateLimited     => RateLimited.response
        case e: Throwable    => Response.fromHttpError(HttpError.InternalServerError(cause = Some(e)))
      },
      Some(_)
    )

  def getSessionTapir = ZLayer.fromZIO {
    {
      for {
        request       <- ZIO.service[ServerRequest]
        maybeCookie    = request.cookies.flatMap(_.toOption).find(c => c.name == COOKIE_KEY).map(_.value)
        maybeAuth      = request.headers.find(c => c.name == "Authorization").map(_.value)
        maybeSessionId = maybeCookie.orElse(maybeAuth)
        session       <- getSession(maybeSessionId)
      } yield session
    }.mapError {
      case u: Unauthorized => TapirResponse(StatusCode.Unauthorized, u.message)
      case RateLimited     => TapirResponse(StatusCode.TooManyRequests)
      case e: Throwable    => TapirResponse(StatusCode.InternalServerError, e.getMessage)
    }
  }

  def getSessionAndSpotifyTapir[R: Tag]: ZLayer[
    RedisService with UserSessionService with ServerRequest with SttpBackend[Task, Any] with Ref[Option[Long]] with R,
    TapirResponse,
    UserSession with SpotifyService with R] =
    getSessionTapir ++ (getSessionTapir >>> ZLayer.fromZIO {
      for {
        session <- ZIO.service[UserSession]
        spotify <- SpotifyService.live(session.accessToken)
      } yield spotify
    }) ++ ZLayer.fromZIOEnvironment(ZIO.environment[R])

  private def extractRequestAuth(headers: Headers) = {
    val cookie = headers.header(Header.Cookie).flatMap { c => c.value.find { c => c.name == COOKIE_KEY }.map(_.content) }

    val token = headers.header(Header.Authorization).flatMap {
      case Authorization.Bearer(token) => Some(token)
      case _                           => None
    }
    cookie.orElse(token)
  }

}
