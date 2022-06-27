package muse.server.graphql

import caliban.schema.Annotations.GQLDefault

// TODO: why isn't this default working?
final case class Pagination(first: Int, @GQLDefault("0") from: Int = 0) {
  def annotation = s"{first: $first, from: $from}"
}

object Pagination {
  def unapply(p: Pagination): (Int, Int) = p.first -> p.from

  object Default {
    val Search = Pagination(10, 0)
  }
}
