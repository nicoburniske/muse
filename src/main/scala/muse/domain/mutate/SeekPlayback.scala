package muse.domain.mutate

import caliban.schema.{ArgBuilder, Schema}
final case class SeekPlayback(deviceId: Option[String], positionMs: Int) derives Schema.SemiAuto, ArgBuilder
