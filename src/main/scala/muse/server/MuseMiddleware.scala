package muse.server

import caliban.Value.StringValue
import caliban.interop.tapir.{StreamTransformer, WebSocketHooks}
import caliban.{CalibanError, GraphQLInterpreter, GraphQLWSOutput, InputValue, ZHttpAdapter}
import io.netty.handler.codec.http.HttpHeaderNames
import muse.domain.error.{MuseError, RateLimited, Unauthorized}
import muse.domain.session.UserSession
import muse.server.graphql.MuseGraphQL
import muse.service.spotify.{SpotifyAPI, SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import muse.utils.Utils
import nl.vroste.rezilience.Bulkhead.{BulkheadError, BulkheadException}
import nl.vroste.rezilience.Bulkhead
import sttp.client3.SttpBackend
import zhttp.http.middleware.HttpMiddleware
import zhttp.http.{HExit, Header, Headers, Http, HttpApp, HttpData, HttpError, Method, Middleware, Request, Response, Status}
import zio.*
import zio.stream.ZStream

import java.time.Instant

object MuseMiddleware {
  def checkAuthAddSession[R](app: Http[R, Throwable, Request, Response]) =
    Http
      .fromFunctionHExit[Request] { request =>
        extractRequestAuth(request) match
          case None       => HExit.fail(Unauthorized("Missing Auth"))
          case Some(auth) =>
            HExit.Effect {
              for {
                session <- UserSessions.getUserSession(auth).mapError(Some(_))
                _       <- RequestSession.set[UserSession](Some(session))
                _       <- RequestSession.set[SpotifyService](Some(session.spotifyService))
                bulkHead = session.bulkhead
                res     <- bulkHead(app(request)).mapError {
                             // When maybeError is None other routers can handle the request.
                             case Bulkhead.WrappedError(maybeError) => maybeError
                             case Bulkhead.BulkheadRejection        => Some(RateLimited)
                           }
              } yield res
            }
      }

  private def extractRequestAuth(request: Request) = request
    .cookieValue(COOKIE_KEY)
    .orElse(request.authorization)
    .map(_.toString)

  val handleErrors: HttpMiddleware[Any, Throwable] = new HttpMiddleware[Any, Throwable] {
    override def apply[R1 <: Any, E1 >: Throwable](http: Http[R1, E1, Request, Response]) = http
      .tapErrorZIO {
        case u: MuseError => ZIO.logInfo(u.message)
        case t: Throwable =>
          val cause   = Option(t.getCause).map(_.toString).getOrElse("")
          val message = s"Something went wrong!. Exception: ${t.toString}. cause:$cause"
          ZIO.logError(message)
      }.catchAll {
        case u: Unauthorized => u.http
        case RateLimited     => RateLimited.http
        case t: Throwable    =>
          Http.error(HttpError.InternalServerError("Something went wrong.", Some(t)))
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

    private val makeSessionRef = Ref.make[Option[UserSession]](None)

    def live[R](interpreter: GraphQLInterpreter[R, CalibanError]) = Http
      .fromFunctionZIO[Request] { request =>
        val maybeAuth = extractRequestAuth(request)
        MuseMiddleware.Websockets.configure(interpreter, maybeAuth)
      }.flatten

    def configure[R](interpreter: GraphQLInterpreter[R, CalibanError], maybeUserSessionId: Option[String]) = for {
      ref        <- makeSessionRef
      authSession = createSession[UserSession](ref)
      // Authorize with cookie!
      _          <- initSession(authSession, maybeUserSessionId).ignore
    } yield {
      // Authorize with input value!
      val connectionInit = WebSocketHooks.init { payload =>
        val maybeSessionId = payload match
          case InputValue.ObjectValue(fields) =>
            fields.get("Authorization").flatMap {
              case StringValue(s) => Some(s)
              case _              => None
            }
          case _                              => None
        initSession(authSession, maybeSessionId)
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

    // If the session is already set, do nothing. Otherwise, attempt to initialize UserSession.
    private def initSession(ref: RequestSession[UserSession], maybeUserSessionId: Option[String]) = ref.get.isSuccess.flatMap {
      case true  => ZIO.unit
      case false =>
        ZIO
          .fromOption(maybeUserSessionId).orElseFail(Unauthorized("Missing Auth: Unable to decode payload"))
          .flatMap(auth => UserSessions.getUserSession(auth))
          .flatMap(session => ref.set(Some(session)))
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

}
