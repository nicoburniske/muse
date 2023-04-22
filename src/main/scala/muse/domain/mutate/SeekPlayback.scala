package muse.domain.mutate

final case class SeekPlayback(deviceId: Option[String], positionMs: Int)
