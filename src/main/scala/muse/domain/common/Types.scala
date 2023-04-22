package muse.domain.common

import zio.prelude.Subtype

object Types {

  object UserId extends Subtype[String]
  type UserId = UserId.Type

  object SessionId extends Subtype[String]
  type SessionId = SessionId.Type

  object AccessToken extends Subtype[String]
  type AccessToken = AccessToken.Type

  object RefreshToken extends Subtype[String]
  type RefreshToken = RefreshToken.Type

}
