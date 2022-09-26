package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.spotify.{AuthCodeFlowData, ClientCredentialsFlowData, RefreshAuthData}
import muse.service.UserSessions
import zhttp.http.{HeaderValues, Headers, HttpData, Method, Path, Response, Scheme, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Task, ZIO}

object SpotifyAuthService {
  type AuthEnv = SpotifyConfig & EventLoopGroup & ChannelFactory

  val ENDPOINT = URL(
    Path.decode("/api/token"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
  )

  def getClientCredentials = for {
    c <- ZIO.service[SpotifyConfig]
    _ <- ZIO.debug(s"Spotify Config: $c")
    body = Map("grant_type" -> "client_credentials")
    response <- executePost(c, body)
    body <- response.bodyAsString
    authData <- deserializeBodyOrFail[ClientCredentialsFlowData](body)
  } yield authData

  // Authorization code flow.
  def getAuthCode(authCode: String): ZIO[AuthEnv, Throwable, AuthCodeFlowData] =
    for {
      response <- requestAccessToken(authCode)
      body <- response.bodyAsString
      authData <- deserializeBodyOrFail[AuthCodeFlowData](body)
    } yield authData

  def requestNewAccessToken(refreshToken: String): ZIO[AuthEnv, Throwable, RefreshAuthData] =
    for {
      response <- refreshAccessToken(refreshToken)
      body <- response.bodyAsString
      authData <- deserializeBodyOrFail[RefreshAuthData](body)
    } yield authData

  private def deserializeBodyOrFail[T](body: String)(using decoder: JsonDecoder[T]) =
    body
      .fromJson[T]
      .fold(
        e => ZIO.fail(SpotifyError.JsonError(e, body)),
        ZIO.succeed(_)
      )

  def requestAccessToken(code: String): ZIO[AuthEnv, Throwable, Response] =
    ZIO.service[SpotifyConfig].flatMap { c =>
      val body = Map(
        "grant_type" -> "authorization_code",
        "code" -> code,
        "redirect_uri" -> c.redirectURI
      )
      executePost(c, body)
    }

  def refreshAccessToken(refreshToken: String) =
    ZIO.service[SpotifyConfig].flatMap { c =>
      val body = Map(
        "grant_type"    -> "refresh_token",
        "refresh_token" -> refreshToken,
        "redirect_uri"  -> c.redirectURI
      )
      executePost(c, body)
    }

  private def executePost(c: SpotifyConfig, body: Map[String, String]) = {
    val headers = Headers.basicAuthorizationHeader(c.clientID, c.clientSecret) ++
      Headers.contentType(HeaderValues.applicationXWWWFormUrlencoded)
    Client.request(ENDPOINT.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
  }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")
}
