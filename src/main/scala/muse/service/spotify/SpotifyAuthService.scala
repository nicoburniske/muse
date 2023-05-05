package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.spotify.auth.*
import muse.service.UserSessions
import zio.http.*
import zio.json.*
import zio.{Task, ZIO, ZLayer}

trait SpotifyAuthService {
  def getClientCredentials: Task[ClientCredentialsFlowData]
  def getAuthCode(code: String): Task[AuthCodeFlowData]
  def requestNewAccessToken(refreshToken: String): Task[RefreshAuthData]
}

object SpotifyAuthService {
  val layer = ZLayer.fromZIO {
    for {
      config <- ZIO.service[SpotifyConfig]
      client <- ZIO.service[Client]
    } yield SpotifyAuthLive(config, client)
  }

  def getClientCredentials = ZIO.serviceWithZIO[SpotifyAuthService](_.getClientCredentials)

  def getAuthCode(code: String) = ZIO.serviceWithZIO[SpotifyAuthService](_.getAuthCode(code))

  def requestNewAccessToken(refreshToken: String) =
    ZIO.serviceWithZIO[SpotifyAuthService](_.requestNewAccessToken(refreshToken))

}

case class SpotifyAuthLive(config: SpotifyConfig, client: Client) extends SpotifyAuthService {

  val TOKEN_ENDPOINT = URL(
    Path.decode("/api/token"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
  )

  val layer = ZLayer.succeed(client)

  override def getClientCredentials = (for {
    response     <- executePost(Map("grant_type" -> "client_credentials"))
    body         <- response.body.asString
    deserialized <- deserializeBodyOrFail[ClientCredentialsFlowData](response.status, body)
  } yield deserialized).provide(layer)

  override def getAuthCode(code: String) =
    (for {
      response     <- executePost(
                        Map(
                          "grant_type"   -> "authorization_code",
                          "code"         -> code,
                          "redirect_uri" -> config.redirectURI
                        ))
      body         <- response.body.asString
      deserialized <- deserializeBodyOrFail[AuthCodeFlowData](response.status, body)
    } yield deserialized).provide(layer)

  override def requestNewAccessToken(refreshToken: String) =
    (for {
      response     <- executePost(
                        Map(
                          "grant_type"    -> "refresh_token",
                          "refresh_token" -> refreshToken,
                          "redirect_uri"  -> config.redirectURI
                        ))
      body         <- response.body.asString
      deserialized <- deserializeBodyOrFail[RefreshAuthData](response.status, body)
    } yield deserialized).provide(layer)

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
    val headers = Headers(
      Header.Authorization.Basic(config.clientID, config.clientSecret),
      Header.ContentType(MediaType.application.`x-www-form-urlencoded`)
    )
    Client.request(TOKEN_ENDPOINT.encode, Method.POST, headers, Body.fromString(encodeFormBody(body)))
  }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")
}
