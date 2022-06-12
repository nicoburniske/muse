package domain.spotify
import zio.json.*

final case class MultiArtist(artists: Vector[Artist])

object MultiArtist {
  given decoder: JsonDecoder[MultiArtist] = DeriveJsonDecoder.gen[MultiArtist]
}
