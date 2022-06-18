package muse.domain.spotify
import zio.json.*

final case class MultiArtist(artists: Vector[Artist])

object MultiArtist {
  given decoder: JsonCodec[MultiArtist] = DeriveJsonCodec.gen[MultiArtist]
}
