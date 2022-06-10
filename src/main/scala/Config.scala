import zio.config.*
import ConfigDescriptor.*
import ZConfig.*

final case class AppConfig(spotify: SpotifyConfig, sqlConfig: SqlConfig)
final case class SpotifyConfig(clientID: String, clientSecret: String, redirectURI: String)
final case class SqlConfig(database: String, host: String, port: Int, user: String, password: String)
object AppConfig {
  val appDescriptor: ConfigDescriptor[AppConfig] =
    (nested("spotify")(spotifyDescriptor) zip
      nested("db")(sqlDescriptor)).to[AppConfig]

  val spotifyDescriptor: ConfigDescriptor[SpotifyConfig] =
    (string("client_id") zip string("client_secret") zip string("redirect_uri")).to[SpotifyConfig]

  val sqlDescriptor: ConfigDescriptor[SqlConfig]         =
    (string("database") zip
      string("host") zip
      ConfigDescriptor.int("port") zip
      string("user") zip
      string("password")).to[SqlConfig]
}
