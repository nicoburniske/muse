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
//  RequestHandlerMiddleware.WithOut[
//    R0 with R with Context,
//    R,
//    E,
//    Any,
//    ({type OutEnv[Env] = R0 with R})#OutEnv,
//    ({type OutErr[Err] = Err})#OutErr,
//  ]

  def InjectSessionAndRateLimit[R]:
//    RequestHandlerMiddleware.WithOut[
//    RedisService & UserSessionService & UserSession,
//    RedisService & UserSessionService,
//    Response,
//    Any,
//    RedisService & UserSessionService,
//    Response,
//  ]
  RequestHandlerMiddleware.Contextual[
    RedisService & UserSessionService & UserSession,
    RedisService & UserSessionService & R,
    Response,
    Any,
  ] = HttpAppMiddleware.customAuthProvidingZIO(
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

  /**
   * Caliban / Tapir related middleware
   */

  val getSessionTapir: ZLayer[RedisService with UserSessionService with ServerRequest, TapirResponse, UserSession] =
    ZLayer.fromZIO {
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

//  /**
//   * Provide UserSession and SpotifyService to GraphQL Interpreter.
//   */
//  def getSessionAndSpotifyTapir[R](makeSpotify: String => SpotifyService) = {
//    ZLayer.fromZIOEnvironment(ZIO.environment[R]) ++
//      getSessionTapir ++ {
//        getSessionTapir >>> ZLayer.fromZIO {
//          ZIO.service[UserSession].map(session => makeSpotify(session.accessToken))
//        }
//      }
//  }

  /**
   * Provide UserSession and SpotifyService to GraphQL Interpreter.
   */
//  def getSessionAndSpotifyTapir[R] = {
//    ZLayer.environment[R] ++
//      getSessionTapir >+> ZLayer.fromZIO {
//        ZIO.serviceWithZIO[UserSession](session => SpotifyService.live(session.accessToken))
//      }
//  }

  def getSessionAndSpotifyTapir[R] = ZLayer.makeSome[
    R with UserSessionService with RedisService with ServerRequest with SpotifyService.Env,
    R with UserSession with SpotifyService
  ](
    getSessionTapir,
    ZLayer.fromZIO(ZIO.serviceWithZIO[UserSession](session => SpotifyService.live(session.accessToken)))
  )

  private def extractRequestAuth(headers: Headers) = {
    val cookie = headers.header(Header.Cookie).flatMap { c => c.value.find { c => c.name == COOKIE_KEY }.map(_.content) }

    val token = headers.header(Header.Authorization).flatMap {
      case Authorization.Bearer(token) => Some(token)
      case _                           => None
    }
    cookie.orElse(token)
  }

}
