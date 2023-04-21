package muse.server.graphql.resolver

import caliban.schema.Schema
import muse.domain.common.Types.UserId
import muse.domain.session.UserSession
import muse.server.graphql.Pagination
import muse.server.graphql.subgraph.Playlist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import zio.ZIO
import zio.query.ZQuery

final case class UserPlaylistsInput(pagination: Option[Pagination])

object GetUserPlaylists:
  def boxedQuery(userId: UserId)(input: UserPlaylistsInput)
      : ZQuery[RequestSession[SpotifyService] & RequestSession[UserSession], Throwable, List[Playlist]] =
    query(userId)(input.pagination)

  def query(userId: UserId)(p: Option[Pagination]) = ZQuery.fromZIO {
    userPlaylistsZIO(userId, p)
  }

  def userPlaylistsZIO(userId: UserId, p: Option[Pagination]) = for {
    spotify  <- RequestSession.get[SpotifyService]
    indices  <- getUserPlaylistIndices(userId, p)
    results  <- ZIO.foreachPar(indices)(index => spotify.getUserPlaylists(userId, 50, Some(index)))
    playlists = results.flatMap(_.items).toList
  } yield playlists.map(Playlist.fromSpotify)

  def getUserPlaylistIndices(userId: UserId, p: Option[Pagination]) = p match
    case Some(p) =>
      ZIO.succeed(getIndicesPagination(p))
    case None    =>
      for {
        spotify <- RequestSession.get[SpotifyService]
        total   <- spotify.getUserPlaylists(userId, 1).map(_.total)
      } yield (0 until total).grouped(50).map(_.start).toVector

  def getIndicesPagination(p: Pagination) = (p.offset until p.offset + p.first).grouped(50).map(_.start).toVector
