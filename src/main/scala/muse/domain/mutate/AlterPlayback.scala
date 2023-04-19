package muse.domain.mutate
import caliban.schema.{ArgBuilder, Schema}

final case class AlterPlayback(deviceId: Option[String]) derives Schema.SemiAuto, ArgBuilder
