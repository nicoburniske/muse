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
  def checkAuthAddSession[R](app: Http[R & SpotifyService, Throwable, Request, Response]) =
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
            session <- getSession(auth)
            _       <- RequestSession.set[UserSession](Some(session))
            // Build layer using Environment.
            layer   <- SpotifyService.getLayer
          } yield app.provideSomeLayer[R, SpotifyService, Throwable](layer)
        }
      }
      .flatten

  def getSession(cookie: String): ZIO[SpotifyAuthService & UserSessions, Throwable, UserSession] =
    for {
      maybeUser      <- UserSessions.getUserSession(cookie)
      session        <- ZIO.fromOption(maybeUser).orElseFail(Unauthorized("Invalid Auth"))
      updatedSession <-
        if (session.expiration.isAfter(Instant.now()))
          ZIO.logInfo(s"Session Retrieved: ${session.conciseString}").as(session)
        else
          for {
            authData      <- SpotifyAuthService.requestNewAccessToken(session.refreshToken)
            newExpiration <- Utils.getExpirationInstant(authData.expiresIn)
            newSession    <- UserSessions.updateUserSession(session.sessionCookie) {
                               _.copy(accessToken = authData.accessToken, expiration = newExpiration)
                             }
            // These 'get' calls should be a-ok.
            _             <- ZIO.logInfo(s"Session Updated ${newSession.get.conciseString}")
          } yield newSession.get
    } yield updatedSession

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

//  object Websockets {
//    private val wsSession = Http.fromZIO(Ref.make[Option[UserSession]](None))
//
//    def live[R <: RequestSession[UserSession] & UserSessions & SpotifyAuthService & SttpBackend[Task, Any]](
//        interpreter: GraphQLInterpreter[R, CalibanError]
//    ) =
//      wsSession.flatMap { wsSesh =>
//        val auth =
//          new RequestSession[UserSession] {
//            def get: IO[Unauthorized, UserSession]           =
//              wsSesh.get.flatMap {
//                case Some(v) => ZIO.succeed(v)
//                case None    => ZIO.fail(Unauthorized("Missing Auth??"))
//              }
//            def set(session: Option[UserSession]): UIO[Unit] = wsSesh.set(session)
//          }
//
//        val connectionInit = WebSocketHooks.init[R, Throwable](payload =>
//          ZIO.logInfo(s"Payload ${payload.toString} ${payload.toInputString}") *>
//            ZIO
//              .fromOption {
//                payload match
//                  case InputValue.ObjectValue(fields) =>
//                    fields.get("Authorization").orElse(fields.get(COOKIE_KEY)).flatMap {
//                      case StringValue(s) => Some(s)
//                      case _              => None
//                    }
//                  case _                              => None
//              }.orElseFail(CalibanError.ExecutionError("Unable to decode payload"))
//              .flatMap(auth => getSession(auth))
//              .flatMap(user => zio.Console.printLine(s"???!${user}") *> auth.set(Some(user))))
//
//        val transformService = WebSocketHooks.message(new StreamTransformer[R, Throwable] {
//          def transform[R1 <: R, E1 >: Throwable](
//              stream: ZStream[R1, E1, GraphQLWSOutput]
//          ): ZStream[R1, E1, GraphQLWSOutput] =
//            for {
//              spot <- ZStream.fromZIO(SpotifyService.live)
//              layer = ZLayer.succeed(spot)
//              s    <- stream.updateService[RequestSession[UserSession]](_ => auth).provideSomeLayer(layer)
//            } yield s
//        })
//
//        ZHttpAdapter.makeWebSocketService(
//          interpreter,
//          webSocketHooks = connectionInit
//        )
//      }
//  }
}
