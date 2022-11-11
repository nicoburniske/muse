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
            session <- UserSessions.getUserSession(auth)
            _       <- ZIO.logInfo(s"Found session for ${session.userId} with session ${session.sessionId}")
            _       <- RequestSession.set[UserSession](Some(session))
            _       <- RequestSession.set[SpotifyService](Some(session.spotifyService))
            _       <- ZIO.logAnnotate("user_id", session.userId)
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
    private val session = Http.fromZIO {
      for {
        ws <- Ref.make[Option[UserSession]](None)
      } yield ws
    }

    def live[R <: UserSessions](interpreter: GraphQLInterpreter[R, CalibanError]) =
      session.flatMap { ref =>
        val authSession = createSession[UserSession](ref)

        val connectionInit = WebSocketHooks.init { payload =>
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
            .flatMap(auth => UserSessions.getUserSession(auth))
            .flatMap(session => authSession.set(Some(session)))
            .unit
        }

        type Env = UserSessions & RequestSession[UserSession] & RequestSession[SpotifyService]
        // Ensure that each message has latest sessions in environment.
        val transformService = WebSocketHooks.message(new StreamTransformer[Env, Throwable] {
          def transform[R1 <: Env, E1 >: Throwable](
              stream: ZStream[R1, E1, GraphQLWSOutput]
          ): ZStream[R1, E1, GraphQLWSOutput] =
            ZStream.fromZIO(
              for {
                sessionId     <- authSession.get.map(_.sessionId)
                latestSession <- UserSessions.getUserSession(sessionId)
                _             <- authSession.set(Some(latestSession)) <&>
                                   RequestSession.set[UserSession](Some(latestSession)) <&>
                                   RequestSession.set[SpotifyService](Some(latestSession.spotifyService))
              } yield ()
            ) *> stream
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
