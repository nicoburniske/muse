package muse.server.graphql

import caliban.schema.Annotations.GQLDefault

final case class Pagination(first: Int, @GQLDefault("0") offset: Int = 0) {
  def annotation = s"{first: $first, offset: $offset}"
}

object Pagination {
  def unapply(p: Pagination): (Int, Int) = p.first -> p.offset

  object Default {
    val Search = Pagination(10, 0)
  }
}
