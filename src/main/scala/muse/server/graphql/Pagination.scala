package muse.server.graphql

enum Pagination:
  case All
  case Offset(first: Int, from: Int)
