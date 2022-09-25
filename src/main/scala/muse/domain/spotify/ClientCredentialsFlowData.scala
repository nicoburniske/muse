package muse.domain.spotify

import zio.json.jsonField
import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder

final case class ClientCredentialsFlowData(
                                            @jsonField("access_token")
                                            accessToken: String,
                                            @jsonField("token_type")
                                            tokenType: String,
                                            @jsonField("expires_in")
                                            expiresIn: Int)

object ClientCredentialsFlowData {
  given decoder: JsonDecoder[ClientCredentialsFlowData] = DeriveJsonDecoder.gen[ClientCredentialsFlowData]
}
