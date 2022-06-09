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
import zio.config.*
import ConfigDescriptor.*
import ZConfig.*
import zio.config.typesafe.TypesafeConfig
import zio.config.yaml.YamlConfig

import java.io.File

object Main extends ZIOAppDefault {
  val scopes = List("playlist-read-collaborative user-read-email").mkString(" ")

  case class AuthData(
      @jsonField("token_type") tokenType: String,
      @jsonField("access_token") accessToken: String,
      @jsonField("refresh_token") refreshToken: String,
      @jsonField("scope") scope: String)

  object AuthData {
    given decoder: JsonDecoder[AuthData] = DeriveJsonDecoder.gen[AuthData]
  }

  case class AppConfig(spotify: SpotifyConfig, sqlConfig: SqlConfig)
  case class SpotifyConfig(clientID: String, clientSecret: String, redirectURI: String)
  case class SqlConfig(name: String, host: String, port: Int, username: String, password: String)
  object AppConfig {
    val appDescriptor: ConfigDescriptor[AppConfig]         =
      (nested("spotify")(spotifyDescriptor) zip nested("database")(sqlDescriptor)).to[AppConfig]
    val spotifyDescriptor: ConfigDescriptor[SpotifyConfig] =
      (string("client_id") zip string("client_secret") zip string("redirect_uri")).to[SpotifyConfig]
    val sqlDescriptor: ConfigDescriptor[SqlConfig]         =
      (string("name") zip
        string("host") zip
        ConfigDescriptor.int("port") zip
        string("username") zip
        string("password")).to[SqlConfig]
  }

  def generateRedirectUrl(): URIO[SpotifyConfig, URL] = for {
    c     <- ZIO.serviceWith[SpotifyConfig](identity)
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
      val maybeCode = req.url.queryParams.get("code").flatMap(_.headOption)
      for {
        code                <- ZIO.getOrFailWith(new Exception("Missing code in response"))(maybeCode)
        accessTokenResponse <- getAuthTokens(code)
        _                   <- printLine(accessTokenResponse)
      } yield
      // TODO: yield redirect to website.
      // TODO: add cookie for session.
      // TODO: Save session in memory somewhere
      Response.text("YEET")
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
    ZIO.serviceWith[SpotifyConfig](identity).flatMap { c =>
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
  val appConfig               = TypesafeConfig.fromHoconFile(new File("application.conf"), AppConfig.appDescriptor)
  val flattenedAppConfigLayer = appConfig.flatMap { zlayer =>
    ZLayer.succeed(zlayer.get.spotify) ++ ZLayer.succeed(zlayer.get.sqlConfig)
  }
  val allLayers               = clientLayer ++ flattenedAppConfigLayer ++ zio.ZEnv.live

  override def run = {
    Server.start(8883, endpoints).exitCode.provideLayer(allLayers)
  }
}
