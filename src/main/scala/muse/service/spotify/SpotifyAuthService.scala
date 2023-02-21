package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.spotify.auth.{AuthCodeFlowData, ClientCredentialsFlowData, RefreshAuthData, SpotifyAuthDeserializationError, SpotifyAuthError, SpotifyAuthErrorResponse}
import muse.service.UserSessions
import zhttp.http.{HeaderValues, Headers, HttpData, Method, Path, Response, Scheme, Status, URL}
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
  } yield SpotifyAuthLive(config, eventLoopGroup, channelFactory))

  def getClientCredentials = ZIO.serviceWithZIO[SpotifyAuthService](_.getClientCredentials)

  def getAuthCode(code: String) = ZIO.serviceWithZIO[SpotifyAuthService](_.getAuthCode(code))

  def requestNewAccessToken(refreshToken: String) =
    ZIO.serviceWithZIO[SpotifyAuthService](_.requestNewAccessToken(refreshToken))

}

case class SpotifyAuthLive(config: SpotifyConfig, eventLoopGroup: EventLoopGroup, channelFactory: ChannelFactory)
    extends SpotifyAuthService {

  val TOKEN_ENDPOINT = URL(
    Path.decode("/api/token"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
  )

  val layer = ZLayer.succeed(eventLoopGroup) ++ ZLayer.succeed(channelFactory)

  override def getClientCredentials = (for {
    response     <- executePost(Map("grant_type" -> "client_credentials"))
    body         <- response.bodyAsString
    deserialized <- deserializeBodyOrFail[ClientCredentialsFlowData](response.status, body)
  } yield deserialized).provideLayer(layer)

  override def getAuthCode(code: String) =
    (for {
      response     <- executePost(
                        Map(
                          "grant_type"   -> "authorization_code",
                          "code"         -> code,
                          "redirect_uri" -> config.redirectURI
                        ))
      body         <- response.bodyAsString
      deserialized <- deserializeBodyOrFail[AuthCodeFlowData](response.status, body)
    } yield deserialized).provideLayer(layer)

  override def requestNewAccessToken(refreshToken: String) =
    (for {
      response     <- executePost(
                        Map(
                          "grant_type"    -> "refresh_token",
                          "refresh_token" -> refreshToken,
                          "redirect_uri"  -> config.redirectURI
                        ))
      body         <- response.bodyAsString
      deserialized <- deserializeBodyOrFail[RefreshAuthData](response.status, body)
    } yield deserialized).provideLayer(layer)

  private def deserializeBodyOrFail[T: JsonDecoder](status: Status, body: String) =
    body
      .fromJson[T]
      .fold(
        _ => deserializeErrorBody(status, body),
        ZIO.succeed(_)
      )

  private def deserializeErrorBody(status: Status, body: String) =
    body
      .fromJson[SpotifyAuthErrorResponse]
      .fold(
        e => ZIO.fail(SpotifyAuthDeserializationError("Failed to deserialize error body", e, body)),
        ZIO.fail(_)
      ).mapError(SpotifyAuthError(status, _))

  private def executePost(body: Map[String, String]) = {
    val headers = Headers.basicAuthorizationHeader(config.clientID, config.clientSecret) ++
      Headers.contentType(HeaderValues.applicationXWWWFormUrlencoded)
    Client.request(TOKEN_ENDPOINT.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
  }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")
}
