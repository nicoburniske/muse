package muse.server

import caliban.*
import caliban.execution.QueryExecution
import caliban.interop.tapir.WebSocketInterpreter
import caliban.interop.tapir.HttpInterpreter
import com.stuart.zcaffeine.Cache
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{AppConfig, ServerConfig, SpotifyConfig, SpotifyServiceConfig}
import muse.domain.error.Unauthorized
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
    _            <- MigrationService.runMigrations
    allEndpoints <- makeEndpoints
    _            <- Server.serve(allEndpoints) <&> metricsServer
  } yield ()

  def makeEndpoints = for {
    cors <- getCorsConfig
    gql  <- endpointsGraphQL
  } yield {
    val middleware       = MuseMiddleware.InjectSessionAndRateLimit[MuseGraphQL.ServiceEnv & SpotifyService.Env]
    val protectedZioHttp = Auth.sessionEndpoints @@ middleware

    (gql ++ protectedZioHttp ++ Auth.loginEndpoints) @@ cors @@ metrics()
  }

  val endpointsGraphQL = {
    import sttp.tapir.json.zio.*
    for {
      interpreter <- MuseGraphQL.interpreter
    } yield {
      val interceptor = MuseMiddleware.getSessionAndSpotifyTapir[MuseGraphQL.ServiceEnv]

      Http.collectHttp[Request] {
        case _ -> !! / "api" / "graphql" =>
          ZHttpAdapter
            .makeHttpService(
              HttpInterpreter(interpreter)
                .configure(Configurator.setQueryExecution(QueryExecution.Batched))
                .intercept(interceptor)
            ).mapError { case t: Throwable => Response.fromHttpError(HttpError.InternalServerError(cause = Some(t))) }

        case _ -> !! / "ws" / "graphql" =>
          ZHttpAdapter.makeWebSocketService(
            WebSocketInterpreter(interpreter)
              .intercept(interceptor)
          )
      }
    }
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
