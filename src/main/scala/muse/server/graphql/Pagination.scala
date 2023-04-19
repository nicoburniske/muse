package muse.server.graphql

import caliban.schema.Annotations.GQLDefault
import caliban.schema.{ArgBuilder, Schema}

final case class Pagination(first: Int, @GQLDefault("0") offset: Int = 0) derives Schema.SemiAuto, ArgBuilder {
  def annotation = s"{first: $first, offset: $offset}"
}

object Pagination {
  def unapply(p: Pagination): (Int, Int) = p.first -> p.offset

  object Default {
    val Search = Pagination(10, 0)
  }
}
