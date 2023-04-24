package muse.domain.common

import zio.prelude.Subtype
import zio.schema.Schema

object Types {

  object UserId extends Subtype[String]
  type UserId = UserId.Type
  given Schema[UserId] = Schema.primitive[String].transform(UserId(_), UserId.unwrap)

  object SessionId extends Subtype[String]
  type SessionId = SessionId.Type
  given Schema[SessionId] = Schema.primitive[String].transform(SessionId(_), SessionId.unwrap)

  object AccessToken extends Subtype[String]
  type AccessToken = AccessToken.Type
  given Schema[AccessToken] = Schema.primitive[String].transform(AccessToken(_), AccessToken.unwrap)

  object RefreshToken extends Subtype[String]
  type RefreshToken = RefreshToken.Type
  given Schema[RefreshToken] = Schema.primitive[String].transform(RefreshToken(_), RefreshToken.unwrap)

}
