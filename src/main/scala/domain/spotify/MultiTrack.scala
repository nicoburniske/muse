package domain.spotify

import zio.json.*

final case class MultiTrack(tracks: List[Track])

object MultiTrack {
  given decoder: JsonDecoder[MultiTrack] = DeriveJsonDecoder.gen[MultiTrack]
}
