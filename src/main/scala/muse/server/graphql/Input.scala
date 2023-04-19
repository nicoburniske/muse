package muse.server.graphql

import caliban.schema.{ArgBuilder, Schema}
final case class Input[T](input: T) derives Schema.SemiAuto, ArgBuilder
