package muse.server.graphql.resolver

import caliban.relay.{Base64Cursor, Connection, Edge, ForwardPaginationArgs, PageInfo}
import muse.domain.common.EntityType
import muse.domain.spotify
import muse.server.graphql.subgraph.{Album, Artist, Playlist, SearchResult, Track}
import muse.server.graphql.Pagination
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

object GetSearch {
  def query(query: String, entityTypes: Set[EntityType], p: Pagination) = ZQuery.fromZIO {
    val (first, offset) = p match
      case Pagination.All            => (0, 100)
      case Pagination.Offset(f, off) => (f, off)
    SpotifyService.search(query, entityTypes, first, Some(offset)).map {
      case spotify.SearchResult(albums, artists, playlists, tracks) =>
        SearchResult(
          albums.fold(Nil)(_.items.toList).map(Album.fromSpotify),
          artists.fold(Nil)(_.items.toList).map(Artist.fromSpotify),
          playlists.fold(Nil)(_.items.toList).map(Playlist.fromSpotify),
          tracks.fold(Nil)(_.items.toList).map(Track.fromSpotify(_))
        )
    }
  }
}
