package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.spotify
import muse.server.graphql.Pagination
import muse.server.graphql.subgraph.*
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.query.ZQuery

object GetSearch {

  case class PaginationResult[T](limit: Int, nextOffset: Option[Int], itemsLeft: Int, items: List[T])

  def createPaginationResult[T, R](paging: spotify.Paging[T], f: T => R) = {
    (paging.offset, paging.limit, paging.next) match {
      case (Some(offset), Some(limit), Some(_)) =>
        val itemsLeft  = paging.total - (offset + limit)
        val nextOffset = if (itemsLeft > 0) Some(offset + limit) else None
        Some(PaginationResult(limit, nextOffset, itemsLeft, paging.items.toList.map(f)))
      case _                                    => None
    }
  }

  def query(query: String, entityTypes: Set[EntityType], p: Pagination) = ZQuery.fromZIO {
    val Pagination(first, offset) = p
    RequestSession.get[SpotifyService].flatMap(_.search(query, entityTypes, first, Some(offset))).map {
      case spotify.SearchResult(albums, artists, playlists, tracks) =>
        SearchResult(
          albums.flatMap(a => createPaginationResult(a, Album.fromSpotifySimple)),
          artists.flatMap(a => createPaginationResult(a, Artist.fromSpotifySimple)),
          playlists.flatMap(p => createPaginationResult(p, Playlist.fromSpotifySimple)),
          tracks.flatMap(t => createPaginationResult(t, t => Track.fromSpotifySimple(t, None)))
        )
    }
  }
}
