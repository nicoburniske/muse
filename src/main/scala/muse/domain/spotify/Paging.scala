package muse.domain.spotify
import zio.json.*

final case class Paging[T](
    href: Option[String],
    items: Vector[T],
    limit: Option[Int],
    next: Option[String],
    offset: Option[Int],
    previous: Option[String],
    total: Int)

object Paging {
  given decodePaging[T: JsonDecoder]: JsonDecoder[Paging[T]] = DeriveJsonDecoder.gen[Paging[T]]
}
