package muse.server

import caliban.*
import caliban.execution.QueryExecution
import caliban.interop.tapir.{HttpInterpreter, WebSocketHooks, WebSocketInterpreter}
import io.netty.handler.codec.http.HttpHeaderNames
import muse.config.{AppConfig, ServerConfig, SpotifyConfig, SpotifyServiceConfig}
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.server.MuseMiddleware
import muse.server.graphql.MuseGraphQL
import muse.server.graphql.MuseGraphQL.Env
import muse.service.UserSessionService
import muse.service.cache.RedisService
import muse.service.event.ReviewUpdateService
import muse.service.persist.{DatabaseService, MigrationService}
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.utils.Utils
import sttp.client3.SttpBackend
import zio.{ZIO, *}
import zio.http.HttpAppMiddleware.{cors, metrics}
import zio.http.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

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

    // ORDER MATTERS HERE. LOGIN ENDPOINTS MUST BE BEFORE PROTECTED ENDPOINTS.
    // For some reason beautify errors is not working?
    (gql ++ Auth.loginEndpoints ++ protectedZioHttp).mapError { response =>
      if response.status.isError then
        Response.GetError.unapply(response).fold(response) { error =>
          response.copy(
            body = Body.fromString(error.message),
            headers = Headers(Header.ContentType(MediaType.text.`plain`))
          )
        }
      else response
    } @@ cors @@ metrics()
  }

  def endpointsGraphQL = {
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
          val reload = ZIO.serviceWithZIO[Reloadable[UserSession]](_.reload) *>
            ZIO.serviceWithZIO[Reloadable[SpotifyService]](_.reload)

          ZHttpAdapter.makeWebSocketService(
            WebSocketInterpreter(
              interpreter,
              Some(1.minute),
              // Ensure that sessions are reloaded every minute.
              WebSocketHooks.pong(_ => reload)
            )
              .intercept(interceptor)
          )
      }
    }
  }

  def getCorsConfig = {
    import zio.http.internal.middlewares.Cors.CorsConfig

    for {
      maybeDomain <- ZIO.serviceWith[ServerConfig](_.domain)
    } yield cors(CorsConfig(allowedOrigin = origin => {
      maybeDomain match
        case None         => Some(Header.AccessControlAllowOrigin.All)
        case Some(domain) =>
          if (origin.renderedValue.contains(domain)) {
            Some(Header.AccessControlAllowOrigin.Specific(origin))
          } else {
            None
          }
    }))
  }

  def metricsServer = Server
    .serve {
      Http.collectZIO[Request] {
        case Method.GET -> !! / "metrics" => ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }
    }.provideSomeLayer[PrometheusPublisher](Server.defaultWithPort(9091))

  // TODO: Expose this?
  lazy val writeSchemaToFile = for {
    serverConfig <- ZIO.service[ServerConfig]
    _            <- Utils.writeToFile(serverConfig.schemaFile, MuseGraphQL.api.render)
  } yield ()
}
