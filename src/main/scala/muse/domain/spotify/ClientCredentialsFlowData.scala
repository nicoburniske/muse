package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder, SnakeCase, jsonField, jsonMemberNames}

@jsonMemberNames(SnakeCase)
final case class ClientCredentialsFlowData(accessToken: String, tokenType: String, expiresIn: Int)

object ClientCredentialsFlowData {
  given decoder: JsonDecoder[ClientCredentialsFlowData] = DeriveJsonDecoder.gen[ClientCredentialsFlowData]
}
