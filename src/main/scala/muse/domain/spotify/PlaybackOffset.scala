package muse.domain.spotify
import zio.json.*

sealed trait PlaybackOffset

case class PositionOffset(position: Int) extends PlaybackOffset
case class UriOffset(uri: String)        extends PlaybackOffset

object PlaybackOffset:
  given pos: JsonEncoder[PositionOffset]  = DeriveJsonEncoder.gen[PositionOffset]
  given uri: JsonEncoder[UriOffset]       = DeriveJsonEncoder.gen[UriOffset]
  given both: JsonEncoder[PlaybackOffset] = pos.orElseEither(uri).contramap[PlaybackOffset] {
    case p: PositionOffset => Left(p)
    case u: UriOffset      => Right(u)
  }
