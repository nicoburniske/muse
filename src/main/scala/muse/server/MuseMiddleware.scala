package muse.server

import caliban.*
import caliban.Value.StringValue
import caliban.interop.tapir.TapirAdapter.TapirResponse
import caliban.interop.tapir.{StreamTransformer, WebSocketHooks, WebSocketInterpreter}
import io.netty.handler.codec.http.HttpHeaderNames
import muse.domain.common.Types.SessionId
import muse.domain.error.MuseError
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.UserSessionService
import muse.service.cache.RedisService
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService, SpotifyServiceLive}
import muse.utils.Utils
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import sttp.tapir.model.ServerRequest
import zio.*
import zio.http.*
import zio.http.Header.Authorization
import zio.http.Http.Route
import zio.redis.Redis
import zio.stream.ZStream

import java.time.Instant

object MuseMiddleware {

  /**
   * ZIO Http related middleware
   */

  def InjectSessionAndRateLimit[R] = HttpAppMiddleware.customAuthProvidingZIO[
    UserSessionService,
    UserSessionService & R,
    Response,
    UserSession,
  ](
    getSessionZioHttp,
    Headers.empty,
    Status.Unauthorized
  )

  def getSessionZioHttp(headers: Headers) = getSession(extractRequestAuth(headers))
    .mapBoth(
      {
        case u: Unauthorized => Response.fromHttpError(HttpError.Unauthorized(u.message))
        case RateLimited     => Response.fromHttpError(HttpError.TooManyRequests("Too many concurrent requests"))
        case e: Throwable    => Response.fromHttpError(HttpError.InternalServerError(cause = Some(e)))
      },
      Some(_)
    )

  private def extractRequestAuth(headers: Headers) = {
    val cookie = headers.header(Header.Cookie).flatMap { c => c.value.find { c => c.name == COOKIE_KEY }.map(_.content) }

    val token = headers.header(Header.Authorization).flatMap {
      case Authorization.Bearer(token) => Some(token)
      case _                           => None
    }
    cookie.orElse(token)
  }

  /**
   * Caliban / Tapir related middleware
   */

  val getSessionTapir: ZLayer[UserSessionService with ServerRequest, TapirResponse, UserSession] =
    ZLayer.fromZIO {
      {
        for {
          request       <- ZIO.service[ServerRequest]
          maybeCookie    = request.header(sttp.model.HeaderNames.Cookie).flatMap { value =>
                             value
                               .split(";")
                               .map(_.trim)
                               .map(_.split("="))
                               .filter(_.length == 2)
                               .find(_(0) == COOKIE_KEY).map(_(1))
                           }
          maybeAuth      = request.header(sttp.model.HeaderNames.Authorization).flatMap { value =>
                             val split = value.split(" ")
                             if (split.length == 2 && split(0).toLowerCase == "bearer") Some(split(1))
                             else None
                           }
          maybeSessionId = maybeCookie.orElse(maybeAuth)
          session       <- getSession(maybeSessionId).mapError {
                             case u: Unauthorized => TapirResponse(StatusCode.Unauthorized, u.message)
                             case RateLimited     => TapirResponse(StatusCode.TooManyRequests)
                             case e: Throwable    => TapirResponse(StatusCode.InternalServerError, e.getMessage)
                           }
        } yield session
      }
    }

  // TODO: This will fail after an hour when the access token expires. Make it reloadable?
  def getSessionAndSpotifyTapir[R] = ZLayer.makeSome[
    R with UserSessionService with SpotifyService.Env with ServerRequest,
    R with UserSession with SpotifyService
  ](
    getSessionTapir,
    ZLayer.fromZIO(ZIO.serviceWithZIO[UserSession](session => SpotifyService.live(session.spotifyData.accessToken)))
  )

  /**
   * Implementation.
   */

  private case object RateLimited
  private case class Unauthorized(message: String)

  private type GetSession = ZIO[UserSessionService, Throwable | RateLimited.type | Unauthorized, UserSession]

  private def getSession(maybeSessionId: Option[String]): GetSession = {
    val retrieveSession = maybeSessionId.fold(ZIO.fail(Unauthorized("Missing Session ID."))) { sessionId =>
      UserSessionService
        .getUserSession(SessionId(sessionId))
        .someOrFail[UserSession, Throwable | Unauthorized](Unauthorized("Invalid Session ID."))
        .tapErrorCause { c => ZIO.logErrorCause("Failed to get user session", c) }
    }

    for {
      session <- retrieveSession
      _       <- ZIO
                   .whenZIO[UserSessionService, Throwable | RateLimited.type] {
                     UserSessionService
                       .isRateLimited(session.userId)
                       .tapErrorCause { c => ZIO.logErrorCause("Failed to check rate limit", c) }
                   } {
                     ZIO.logError(s"Rate Limited ${session.userId}") *> ZIO.fail(RateLimited)
                   }
    } yield session
  }

}
