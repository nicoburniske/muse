package domain.spotify
import zio.json.*

final case class MultiArtist(artists: List[Artist])

object MultiArtist {
  given decoder: JsonDecoder[MultiArtist] = DeriveJsonDecoder.gen[MultiArtist]
}
