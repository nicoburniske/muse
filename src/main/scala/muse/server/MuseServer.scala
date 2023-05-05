package muse.server

import caliban.*
import caliban.execution.QueryExecution
import caliban.interop.tapir.WebSocketInterpreter
import caliban.interop.tapir.HttpInterpreter
import com.stuart.zcaffeine.Cache
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{AppConfig, ServerConfig, SpotifyConfig, SpotifyServiceConfig}
import muse.domain.error.{RateLimited, Unauthorized}
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.server.graphql.MuseGraphQL.Env
import muse.service.cache.RedisService
import muse.service.event.ReviewUpdateService
import muse.service.persist.{DatabaseService, MigrationService}
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessionService}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zio.http.RequestHandlerMiddlewares
import zio.http.{HttpError, Method}
import zio.http.*
import zio.http.HttpAppMiddleware.cors
import zio.http.HttpAppMiddleware.metrics
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.*

// TODO: incorporate cookie signing.
val COOKIE_KEY = "XSESSION"

object MuseServer {
  val live = for {
    _                  <- MigrationService.runMigrations
    protectedEndpoints <- createProtectedEndpoints
    cors               <- getCorsConfig
    allEndpoints        = (Auth.loginEndpoints ++ protectedEndpoints) @@ cors @@ metrics()
    _                  <- Server.serve(allEndpoints) <&> metricsServer
  } yield ()

  def createProtectedEndpoints = endpointsGraphQL.map {
    case (rest, websocket) =>
      val protectedRest =
        ((rest ++ Auth.sessionEndpoints)
          @@ MuseMiddleware.InjectSessionAndRateLimit
          @@ RequestHandlerMiddlewares.beautifyErrors)
          .mapError {
            case RateLimited     => RateLimited.response
            case u: Unauthorized => u.response
            case t: Throwable    => Response.fromHttpError(HttpError.InternalServerError(cause = Some(t)))
          }
      protectedRest ++ websocket
  }

  val endpointsGraphQL = {
    import sttp.tapir.json.zio.*
    for {
      interpreter <- MuseGraphQL.interpreter
    } yield (
      Http.collectHttp[Request] {
        case _ -> !! / "api" / "graphql" =>
          ZHttpAdapter
            .makeHttpService(
              HttpInterpreter(interpreter)
                .configure(Configurator.setQueryExecution(QueryExecution.Batched))
                .intercept(MuseMiddleware.getSessionAndSpotifyTapir[DatabaseService & UserSessionService & ReviewUpdateService & Scope])
            )
      },
      Http.collectHttp[Request] {
        case _ -> !! / "ws" / "graphql" =>
          ZHttpAdapter.makeWebSocketService(
            WebSocketInterpreter(
              interpreter
            ).intercept(MuseMiddleware.getSessionAndSpotifyTapir[DatabaseService & UserSessionService & ReviewUpdateService & Scope])
          )
      }
    )
  }

  val getCorsConfig = {
    import zio.http.internal.middlewares.Cors.CorsConfig

    for {
      domain <- ZIO.serviceWith[ServerConfig](_.domain)
    } yield cors(CorsConfig(allowedOrigin = origin => {
      if (domain.contains(origin.renderedValue)) {
        Some(Header.AccessControlAllowOrigin.Specific(origin))
      } else {
        None
      }
    }))
  }

  val metricsServer =
    Server
      .install(metricsRouter).provideSomeLayer[PrometheusPublisher](Server.defaultWithPort(9091))
      .flatMap(p => ZIO.logInfo(s"Metrics server started on port $p"))
      .tapErrorCause(cause => ZIO.logErrorCause(s"Metrics server failed: ${cause.prettyPrint}", cause))
      .forkDaemon

  val metricsRouter: HttpApp[PrometheusPublisher, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "metrics" => ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  }

  // TODO: Expose this?
  lazy val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _            <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
