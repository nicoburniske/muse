package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.spotify.AuthData
import muse.service.UserSessions
import zhttp.http.{HeaderValues, Headers, HttpData, Method, Path, Response, Scheme, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Task, ZIO}

trait SpotifyAuthService {
  def getAuthTokens(code: String): Task[AuthData]
}

object SpotifyAuthServiceLive {
  type AuthEnv = SpotifyConfig & EventLoopGroup & ChannelFactory & UserSessions

  def getAuthTokens(code: String): ZIO[AuthEnv, Throwable, AuthData] =
    for {
      response <- requestAccessToken(code)
      body     <- response.bodyAsString
      token    <- body.fromJson[AuthData] match {
                    case Left(error) => ZIO.fail(SpotifyError.JsonError(error, body))
                    case Right(data) => ZIO.succeed(data)
                  }
    } yield token

  def requestAccessToken(code: String): ZIO[AuthEnv, Throwable, Response] =
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
        Headers.contentType(HeaderValues.applicationXWWWFormUrlencoded)
      Client.request(url.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
    }
  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String                   =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")
}
