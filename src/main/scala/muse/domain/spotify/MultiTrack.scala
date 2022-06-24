package muse.domain.spotify

import zio.json.*

final case class MultiTrack(tracks: Vector[Track])

object MultiTrack {
  given decoder: JsonDecoder[MultiTrack] = DeriveJsonDecoder.gen[MultiTrack]
}
