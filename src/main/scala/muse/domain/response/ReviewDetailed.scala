package muse.domain.response

import muse.domain.common.EntityType
import muse.domain.spotify.{
  AlbumType,
  PlaylistTrack,
  User,
  UserPlaylist,
  Album as SAlbum,
  Artist as SArtist,
  Track as STrack
}
import muse.domain.tables.{Review, ReviewComment}
import zio.json.*

import java.util.UUID

final case class ReviewDetailed[T](review: Review, comments: List[ReviewComment], entity: T)

object ReviewDetailed {
  //  given reviewSummaryCodec[T](using d: JsonCodec[T]): JsonCodec[ReviewDetailed[T]] =
  //    DeriveJsonCodec.gen[ReviewDetailed[T]]
  given reviewSummaryEncoder[T](using d: JsonEncoder[T]): JsonEncoder[ReviewDetailed[T]] =
    DeriveJsonEncoder.gen[ReviewDetailed[T]]
}
