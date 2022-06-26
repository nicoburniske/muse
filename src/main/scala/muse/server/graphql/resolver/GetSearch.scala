package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.spotify
import muse.server.graphql.subgraph.{Album, Artist, Playlist, SearchResult, Track}
import muse.server.graphql.Pagination
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

object GetSearch {
  def query(query: String, entityTypes: Set[EntityType], p: Pagination) = ZQuery.fromZIO {
    val Pagination(first, offset) = p
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
