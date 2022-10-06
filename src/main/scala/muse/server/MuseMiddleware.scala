package muse.server

import caliban.Value.StringValue
import caliban.interop.tapir.{StreamTransformer, WebSocketHooks}
import caliban.{CalibanError, GraphQLInterpreter, GraphQLWSOutput, InputValue, ZHttpAdapter}
import io.netty.handler.codec.http.HttpHeaderNames
import muse.domain.error.Unauthorized
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zhttp.http.middleware.HttpMiddleware
import zhttp.http.{Header, Headers, Http, HttpApp, HttpError, Method, Middleware, Request, Response, Status}
import zio.*
import zio.stream.ZStream

import java.time.Instant

// TODO: add Username log annotation?
object MuseMiddleware {
  def checkAuthAddSession[R](app: Http[R, Throwable, Request, Response]) =
    Http
      .fromFunctionZIO[Request] { request =>
        val maybeAuth = request
          .cookieValue(COOKIE_KEY)
          .orElse(request.authorization)
          .map(_.toString)
        maybeAuth.fold {
          ZIO.logInfo("Missing Auth") *> ZIO.fail(Unauthorized("Missing Auth"))
        } { auth =>
          for {
            sessionAndSpotify <- UserSessions
                                   .getSpotifyService(auth)
                                   .someOrElseZIO(ZIO.fail(Unauthorized("Invalid Auth")))
            (session, spotify) = sessionAndSpotify
            _                 <- RequestSession.set[UserSession](Some(session))
            _                 <- RequestSession.set[SpotifyService](Some(spotify))
          } yield app
        }
      }
      .flatten

  val handleErrors: HttpMiddleware[Any, Throwable] = new HttpMiddleware[Any, Throwable] {
    override def apply[R1 <: Any, E1 >: Throwable](http: Http[R1, E1, Request, Response]) = {
      http
        .tapErrorZIO {
          case u: Unauthorized => ZIO.logInfo(u.message)
          case t: Throwable    => ZIO.logError(s"Something went wrong: ${t.getMessage}")
        }
        .catchAll {
          case u: Unauthorized => u.http
          case t: Throwable    => Http.error(HttpError.InternalServerError("Something went wrong", Some(t)))
        }
    }
  }

  /**
   * Logs the requests made to the server.
   *
   * It also adds a request ID to the logging context, so any further logging that occurs in the handler can be associated with
   * the same request.
   */
  val requestLoggingTrace: HttpMiddleware[Any, Nothing] = new HttpMiddleware[Any, Nothing] {
    override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response]
    ): Http[R1, E1, Request, Response] =
      Http.fromOptionFunction[Request] { request =>
        Random.nextUUID.flatMap { requestId =>
          ZIO.logAnnotate("trace_id", requestId.toString) {
            http(request)
          }
        }
      }
  }

  object Websockets {
    private val sessions = Http.fromZIO {
      for {
        ws   <- Ref.make[Option[UserSession]](None)
        spot <- Ref.make[Option[SpotifyService]](None)
      } yield (ws, spot)
    }

    def live[R <: UserSessions](interpreter: GraphQLInterpreter[R, CalibanError]) =
      sessions.flatMap {
        case (wsRef, spotRef) =>
          val authSession = createSession[UserSession](wsRef)
          val spotSession = createSession[SpotifyService](spotRef)

          val connectionInit = WebSocketHooks.init[R, Throwable](payload =>
            ZIO
              .fromOption {
                payload match
                  case InputValue.ObjectValue(fields) =>
                    fields.get("Authorization").flatMap {
                      case StringValue(s) => Some(s)
                      case _              => None
                    }
                  case _                              => None
              }.orElseFail(Unauthorized("Missing Auth: Unable to decode payload"))
              .flatMap(auth => UserSessions.getSpotifyService(auth)).someOrFail(Unauthorized("Invalid Auth"))
              .flatMap { case (user, spot) => authSession.set(Some(user)) *> spotSession.set(Some(spot)) })

          type Env = UserSessions & RequestSession[UserSession] & RequestSession[SpotifyService]
          val transformService = WebSocketHooks.message(new StreamTransformer[Env, Throwable] {
            def transform[R1 <: Env, E1 >: Throwable](
                stream: ZStream[R1, E1, GraphQLWSOutput]
            ): ZStream[R1, E1, GraphQLWSOutput] =
              for {
                currentSession           <- ZStream.fromZIO(authSession.get)
                // Update global session and refresh.
                spotAndUpdatedSession    <- ZStream
                                              .fromZIO(UserSessions.getSpotifyService(currentSession.sessionCookie))
                                              .someOrFail(Unauthorized("Failed to refresh?"))
                (updatedSession, spotify) = spotAndUpdatedSession
                _                        <- ZStream.fromZIO(authSession.set(Some(updatedSession)))
                _                        <- ZStream.fromZIO(spotSession.set(Some(spotify)))
                result                   <- stream
                                              .updateService[RequestSession[UserSession]](_ => authSession)
                                              .updateService[RequestSession[SpotifyService]](_ => spotSession)

              } yield result
          })

          ZHttpAdapter.makeWebSocketService(
            interpreter,
            webSocketHooks = connectionInit ++ transformService
          )
      }
  }

  private def createSession[R: Tag](ref: Ref[Option[R]]) = {
    new RequestSession[R] {
      def get: IO[Unauthorized, R] =
        ref.get.flatMap {
          case Some(v) => ZIO.succeed(v)
          case None    => ZIO.fail(Unauthorized("Missing Websocket Auth"))
        }

      def set(session: Option[R]): UIO[Unit] = ref.set(session)
    }
  }
}
