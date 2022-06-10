import zio.Console.printLine
import zio.{Layer, Random, System, URIO, ZIO, ZIOAppDefault, ZLayer}
import zhttp.http.{Http, Method, Request, Response, Scheme, URL, *}
import zhttp.service.Client
import zhttp.service.Server
import zhttp.service.EventLoopGroup
import zhttp.service.ChannelFactory

import java.util.Base64
import java.nio.charset.StandardCharsets
import zio.json.*
import zio.config.typesafe.TypesafeConfig
import io.getquill.context.ZioJdbc.*
import io.getquill.context.ZioJdbc.DataSourceLayer
import io.getquill.{PostgresZioJdbcContext, SnakeCase}
import io.getquill.*
import persist.QuillContext

import java.io.File
import zio.ZEnv

object Main extends ZIOAppDefault {
  val scopes = List("playlist-read-collaborative", "user-read-email").mkString(" ")

  case class AuthData(
      @jsonField("token_type") tokenType: String,
      @jsonField("access_token") accessToken: String,
      @jsonField("refresh_token") refreshToken: String,
      @jsonField("scope") scope: String)

  object AuthData {
    given decoder: JsonDecoder[AuthData] = DeriveJsonDecoder.gen[AuthData]
  }

  def generateRedirectUrl(): URIO[SpotifyConfig, URL] = for {
    c     <- ZIO.service[SpotifyConfig]
    state <- Random.nextUUID
  } yield URL(
    Path.decode("authorize"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443),
    Map(
      "response_type" -> List("code"),
      "client_id"     -> List(c.clientID),
      "redirect_uri"  -> List(c.redirectURI),
      "scope"         -> List(scopes),
      "state"         -> List(state.toString.take(15))
    )
  )

  val endpoints = Http.collectZIO[Request] {
    case Method.GET -> !! / "login"          =>
      for {
        url <- generateRedirectUrl()
      } yield Response.redirect(url.encode, false)
    case req @ Method.GET -> !! / "callback" =>
      req.url.queryParams.get("code").flatMap(_.headOption) match {
        case None       =>
          ZIO.succeed(
            Response(
              status = Status.BadRequest,
              data = HttpData.fromString("Missing 'code' query parameter")))
        case Some(code) =>
          for {
            accessTokenResponse <- getAuthTokens(code)
            _                   <- printLine(accessTokenResponse)
          } yield
          // TODO: create cookie for session and store in memory
          // TODO: yield redirect to actual site
          Response.text("You're logged in fool")

      }
  }

  def getAuthTokens(code: String): ZIO[SpotifyConfig & EventLoopGroup & ChannelFactory, Throwable, AuthData] =
    for {
      response <- requestAccessToken(code)
      body     <- response.bodyAsString
      token    <- body.fromJson[AuthData] match {
                    case Left(error) => ZIO.fail(new Exception(error))
                    case Right(data) => ZIO.succeed(data)
                  }
    } yield token

  def requestAccessToken(
      code: String): ZIO[SpotifyConfig & EventLoopGroup & ChannelFactory, Throwable, Response] =
    ZIO.service[SpotifyConfig].flatMap { c =>
      val url     = URL(
        Path.decode("api/token"),
        URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
      )
      val body    = Map(
        "grant_type"   -> "authorization_code",
        "code"         -> code,
        "redirect_uri" -> c.redirectURI
      )
      val headers = Headers.basicAuthorizationHeader(c.clientID, c.clientSecret) ++
        Headers.contentType("application/x-www-form-urlencoded")
      Client.request(url.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
    }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")

  val clientLayer             = EventLoopGroup.auto(8) ++ ChannelFactory.auto
  val appConfig               =
    TypesafeConfig.fromHoconFile(new File("src/main/resources/application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfig.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }
  // TODO: add DB layer.
  val allLayers               = clientLayer ++ flattenedAppConfigLayer ++ ZEnv.live ++ QuillContext.dataSourceLayer
  override def run            = {
    Server.start(8883, endpoints).exitCode.provideLayer(allLayers.orDie)
  }
}
