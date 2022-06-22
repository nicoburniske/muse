package muse.server.graphql.subgraph

final case class SpotifyUser(id: String, href: String, uri: String, externalUrls: Map[String, String])
