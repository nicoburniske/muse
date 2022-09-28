package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.spotify.{AuthCodeFlowData, ClientCredentialsFlowData, RefreshAuthData}
import muse.service.UserSessions
import zhttp.http.{HeaderValues, Headers, HttpData, Method, Path, Response, Scheme, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Task, ZIO, ZLayer}

trait SpotifyAuthService {
  def getClientCredentials: Task[ClientCredentialsFlowData]
  def getAuthCode(code: String): Task[AuthCodeFlowData]
  def requestNewAccessToken(refreshToken: String): Task[RefreshAuthData]
}

object SpotifyAuthService {
  val layer = ZLayer.fromZIO(for {
    config         <- ZIO.service[SpotifyConfig]
    eventLoopGroup <- ZIO.service[EventLoopGroup]
    channelFactory <- ZIO.service[ChannelFactory]
  } yield SpotifyAuthRepo(config, eventLoopGroup, channelFactory))

  def getClientCredentials = ZIO.serviceWithZIO[SpotifyAuthService](_.getClientCredentials)

  def getAuthCode(code: String) = ZIO.serviceWithZIO[SpotifyAuthService](_.getAuthCode(code))

  def requestNewAccessToken(refreshToken: String) =
    ZIO.serviceWithZIO[SpotifyAuthService](_.requestNewAccessToken(refreshToken))

}

case class SpotifyAuthRepo(config: SpotifyConfig, eventLoopGroup: EventLoopGroup, channelFactory: ChannelFactory)
    extends SpotifyAuthService {

  val TOKEN_ENDPOINT = URL(
    Path.decode("/api/token"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
  )

  val layer = ZLayer.succeed(eventLoopGroup) ++ ZLayer.succeed(channelFactory)

  override def getClientCredentials =
    executePost(Map("grant_type" -> "client_credentials"))
      .flatMap(_.bodyAsString)
      .flatMap(deserializeBodyOrFail[ClientCredentialsFlowData])
      .provideLayer(layer)

  override def getAuthCode(code: String) =
    executePost(
      Map(
        "grant_type"   -> "authorization_code",
        "code"         -> code,
        "redirect_uri" -> config.redirectURI
      ))
      .flatMap(_.bodyAsString)
      .flatMap(deserializeBodyOrFail[AuthCodeFlowData])
      .provideLayer(layer)

  override def requestNewAccessToken(refreshToken: String) =
    executePost(
      Map(
        "grant_type"    -> "refresh_token",
        "refresh_token" -> refreshToken,
        "redirect_uri"  -> config.redirectURI
      ))
      .flatMap(_.bodyAsString)
      .flatMap(deserializeBodyOrFail[RefreshAuthData])
      .provideLayer(layer)

  private def deserializeBodyOrFail[T: JsonDecoder](body: String) =
    body
      .fromJson[T]
      .fold(
        e => ZIO.fail(SpotifyError.JsonError(e, body)),
        ZIO.succeed(_)
      )

  private def executePost(body: Map[String, String]) = {
    val headers = Headers.basicAuthorizationHeader(config.clientID, config.clientSecret) ++
      Headers.contentType(HeaderValues.applicationXWWWFormUrlencoded)
    Client.request(TOKEN_ENDPOINT.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
  }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")
}
