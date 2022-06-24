package muse.domain.spotify

import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class Followers(href: Option[String], total: Int)

object Followers {
  given decodeFollowers: JsonDecoder[Followers] = DeriveJsonDecoder.gen[Followers]
}
