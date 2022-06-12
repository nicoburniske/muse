package domain.spotify

package domain.spotify
import zio.json.*

final case class MultiAlbum(albums: Vector[Album])

object MultiAlbum {
  given decoder: JsonDecoder[MultiAlbum] = DeriveJsonDecoder.gen[MultiAlbum]
}
