package muse.server.graphql

import muse.domain.common.EntityType
import muse.domain.spotify
import muse.domain.tables.ReviewComment
import muse.server.graphql.subgraph.{
  Album,
  Artist,
  Comment,
  Playlist,
  PlaylistTrack,
  Review,
  ReviewEntity,
  SpotifyUser,
  Track,
  User
}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyService
import zio.{Chunk, ZIO}
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.util.UUID

object Resolvers {
  // TODO create a check for if the given user exists and return an optional accordingly.
  case class GetUser(id: String) extends Request[Nothing, User]

  def getUser(id: String) = ZQuery.fromRequest(GetUser(id))(UserDataSource)

  val UserDataSource: DataSource[DatabaseQueries, GetUser] =
    DataSource.fromFunction("UserDataSource") { req => subgraph.User(req.id, getUserReviews(req.id)) }

  case class GetUserReviews(userId: String) extends Request[SQLException, List[Review]]

  def getUserReviews(userId: String) = ZQuery.fromRequest(GetUserReviews(userId))(UserReviewsDataSource)

  val UserReviewsDataSource: DataSource[DatabaseQueries, GetUserReviews] =
    DataSource.fromFunctionZIO("UserReviewsDataSource") { req =>
      for {
        reviews <- DatabaseQueries.getUserReviews(req.userId)
      } yield reviews.map { tReview =>
        subgraph.Review(
          tReview.id,
          tReview.createdAt,
          tReview.creatorId,
          tReview.reviewName,
          tReview.isPublic,
          //            _ => getReviewComments(tReview.id))
          getReviewComments(tReview.id),
          tReview.entityId,
          tReview.entityType,
          getEntity(tReview.entityId, tReview.entityType)
        )
      }
    }

  case class GetReview(reviewId: UUID) extends Request[SQLException, Option[Review]]

  def getReview(reviewId: UUID) = ZQuery.fromRequest(GetReview(reviewId))(ReviewDataSource)

  val ReviewDataSource: DataSource[DatabaseQueries, GetReview] =
    DataSource.fromFunctionZIO("ReviewDataSource") { g =>
      DatabaseQueries.getReview(g.reviewId).map {
        _.map { r =>
          subgraph.Review(
            r.id,
            r.createdAt,
            r.creatorId,
            r.reviewName,
            r.isPublic,
            getReviewComments(r.id),
            r.entityId,
            r.entityType,
            getEntity(r.entityId, r.entityType)
          )
        }
      }
    }

  case class GetReviewComments(reviewId: UUID) extends Request[Nothing, List[Comment]]

  def getReviewComments(reviewId: UUID) = ZQuery.fromRequest(GetReviewComments(reviewId))(CommentDataSource)

  // TODO: incoporate permissions. This could be weird on second thought because of permissions.
  val CommentDataSource: DataSource[DatabaseQueries, GetReviewComments] =
    DataSource.Batched.make("ReviewCommentsDataSource") { (requests: Chunk[GetReviewComments]) =>
      requests.toList match
        case req :: Nil =>
          DatabaseQueries
            .getReviewComments(req.reviewId)
            .fold(
              e => CompletedRequestMap.empty.insert(req)(Left(e)),
              comments => CompletedRequestMap.empty.insert(req)(Right(comments.map(tableCommentToComment)))
            )
        case reqs       =>
          val ids = reqs.map(_.reviewId)
          for {
            maybeComments <- DatabaseQueries.getAllReviewComments(ids).either
            _             <- if (maybeComments.isRight) ZIO.logInfo(s"Retrieved reviews: ${ids.mkString(", ")}")
                             else ZIO.logInfo(s"Failed to retrieve reviews: ${ids.mkString(",")}")
          } yield {
            maybeComments match
              case Left(value)        =>
                reqs.foldLeft(CompletedRequestMap.empty)((map, r) => map.insert(r)(Left(value)))
              case Right(allComments) =>
                val grouped = allComments.groupBy(_.reviewId)
                reqs.foldLeft(CompletedRequestMap.empty) { (map, r) =>
                  val asComments: List[Comment] =
                    grouped.getOrElse(r.reviewId, Nil).map(tableCommentToComment)
                  map.insert[SQLException, List[Comment]](r)(Right(asComments))
                }
          }
    }

  def tableCommentToComment(r: ReviewComment) =
    subgraph.Comment(
      r.id,
      r.reviewId,
      r.createdAt,
      r.updatedAt,
      r.parentCommentId,
      r.commenter,
      getUser(r.commenter),
      r.comment,
      r.rating,
      r.entityId,
      r.entityType,
      getEntity(r.entityId, r.entityType)
    )

  def getEntity(entityId: String, entityType: EntityType): ZQuery[SpotifyService, Throwable, ReviewEntity] =
    entityType match
      case EntityType.Album    => getAlbum(entityId)
      case EntityType.Artist   => getArtist(entityId)
      case EntityType.Playlist => getPlaylist(entityId)
      case EntityType.Track    => getTrack(entityId)

  case class GetAlbum(id: String) extends Request[Throwable, Album]

  def getAlbum(albumId: String) = ZQuery.fromRequest(GetAlbum(albumId))(AlbumDataSource)

  val AlbumDataSource: DataSource[SpotifyService, GetAlbum] =
    DataSource.Batched.make("AlbumDataSource") { (reqs: Chunk[GetAlbum]) =>
      reqs.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          SpotifyService
            .getAlbum(head.id)
            .fold(
              e => CompletedRequestMap.empty.insert(head)(Left(e)),
              a => CompletedRequestMap.empty.insert(head)(Right(spotAlbumToAlbum(a))))
        case _           =>
          ZIO
            .foreachPar(reqs.grouped(20).toVector) { reqs =>
              SpotifyService.getAlbums(reqs.map(_.id)).either.map(reqs -> _)
            }
            .map { res =>
              res.foldLeft(CompletedRequestMap.empty) {
                case (map: CompletedRequestMap, (reqs, result)) =>
                  result match
                    case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                    case Right(tracks)   =>
                      val grouped = tracks.map(spotAlbumToAlbum).groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result: Either[NotFoundError, Album] =
                          grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Album)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
    }

  def spotAlbumToAlbum(a: muse.domain.spotify.Album): Album =
    Album(
      a.albumGroup,
      a.albumType.toString.dropRight(1),
      a.externalUrls,
      a.genres.getOrElse(Nil),
      a.id,
      a.images.map(_.url),
      a.label,
      a.name,
      a.popularity,
      a.releaseDate,
      ZQuery.foreachPar(a.artists.map(_.id))(getArtist),
      getAlbumTracks(a.id)
    )

  def getAlbumTracks(albumId: String) =
    ZQuery.fromZIO(SpotifyService.getAllAlbumTracks(albumId)).map(_.map(spotifyTrackToTrack).toList)

  case class NotFoundError(entityId: String, entityType: EntityType) extends Throwable

  def spotifyTrackToTrack(t: spotify.Track) = {
    Track(
      getAlbum(t.album.get.id),
      ZQuery.foreachPar(t.artists.map(_.id))(getArtist),
      t.discNumber,
      t.durationMs,
      t.explicit,
      t.externalUrls,
      t.href,
      t.id,
      t.isPlayable,
      t.name,
      t.popularity,
      t.previewUrl,
      t.trackNumber,
      t.isLocal,
      t.uri
    )
  }

  case class GetTrack(id: String) extends Request[Throwable, Track]

  def getTrack(trackId: String) = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)

  val TrackDataSource: DataSource[SpotifyService, GetTrack] =
    DataSource.Batched.make("TrackDataSource") { reqChunks =>
      reqChunks.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          SpotifyService
            .getTrack(head.id)
            .fold(
              e => CompletedRequestMap.empty.insert(head)(Left(e)),
              t => CompletedRequestMap.empty.insert(head)(Right(spotifyTrackToTrack(t)))
            )
            .zipLeft(ZIO.logInfo(s"Requested single track ${head.id}"))
        case reqs        =>
          // TODO: make constants for max batch size.
          ZIO
            .foreachPar(reqs.grouped(50).toVector) { reqs =>
              SpotifyService.getTracks(reqs.map(_.id)).either.map(reqs -> _)
            }
            .map { res =>
              res.foldLeft(CompletedRequestMap.empty) {
                case (map: CompletedRequestMap, (reqs, result)) =>
                  result match
                    case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                    case Right(tracks)   =>
                      val grouped = tracks.map(spotifyTrackToTrack).groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result: Either[NotFoundError, Track] =
                          grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Track)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
            .zipLeft(ZIO.logInfo("Requested tracks in parallel"))
    }

  case class GetArtist(id: String) extends Request[Throwable, Artist]

  def getArtist(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  val ArtistDataSource: DataSource[SpotifyService, GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      reqs.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          SpotifyService
            .getArtist(head.id)
            .fold(
              e => CompletedRequestMap.empty.insert(head)(Left(e)),
              a => CompletedRequestMap.empty.insert(head)(Right(spotArtistToArtist(a))))
        case _           =>
          ZIO
            .foreachPar(reqs.grouped(20).toVector) { reqs =>
              SpotifyService.getArtists(reqs.map(_.id)).either.map(reqs -> _)
            }
            .map { res =>
              res.foldLeft(CompletedRequestMap.empty) {
                case (map: CompletedRequestMap, (reqs, result)) =>
                  result match
                    case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                    case Right(tracks)   =>
                      val grouped = tracks.map(spotArtistToArtist).groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result: Either[NotFoundError, Artist] =
                          grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Artist)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
    }

  def spotArtistToArtist(a: spotify.Artist) = {
    subgraph.Artist(
      a.externalUrls,
      a.followers.get.total,
      a.genres.getOrElse(Nil),
      a.href,
      a.id,
      a.images.fold(Nil)(_.map(_.url)),
      a.name,
      a.popularity.get,
      getArtistAlbums(a.id),
      getArtistTopTracks(a.id)
    )
  }

  // TODO: maybe think of grouping requests by artist? Are we guaranteed to not have duplicates?
  case class GetArtistAlbums(artistId: String) extends Request[Throwable, List[Album]]

  def getArtistAlbums(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getAllArtistAlbums(artistId)).map(_.map(spotAlbumToAlbum).toList)

  // TODO: incorporate region/country.
  case class GetArtistTopTracks(artistId: String) extends Request[Throwable, List[Track]]

  def getArtistTopTracks(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getArtistTopTracks(artistId)).map(_.map(spotifyTrackToTrack).toList)

  // TODO: link back to user?

  case class GetPlaylist(id: String) extends Request[Throwable, Playlist]

  def getPlaylist(playlistId: String) = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)

  val PlaylistDataSource: DataSource[SpotifyService, GetPlaylist] =
    DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
      SpotifyService.getPlaylist(req.id).map { p =>
        subgraph.Playlist(
          p.collaborative,
          p.description,
          p.externalUrls,
          p.id,
          p.images.map(_.url),
          p.name,
          SpotifyUser(p.owner.id, p.owner.href, p.owner.uri, p.owner.externalUrls),
          p.primaryColor,
          p.public,
          getPlaylistTracks(p.id)
        )
      }
    }

  case class GetPlaylistTracks(playlistId: String) extends Request[Throwable, List[PlaylistTrack]]

  def getPlaylistTracks(playlistId: String) =
    ZQuery.fromRequest(GetPlaylistTracks(playlistId))(PlaylistTrackDataSource)

  val PlaylistTrackDataSource: DataSource[SpotifyService, GetPlaylistTracks] =
    DataSource.fromFunctionZIO("PlaylistTrackDataSource") { reqs =>
      SpotifyService.getAllPlaylistTracks(reqs.playlistId).map {
        _.map { t =>
          subgraph.PlaylistTrack(
            t.addedAt,
            SpotifyUser(t.addedBy.id, t.addedBy.href, t.addedBy.uri, t.addedBy.externalUrls),
            t.isLocal,
            Track(
              getAlbum(t.track.album.get.id),
              ZQuery.foreachPar(t.track.artists.map(_.id))(getArtist),
              t.track.discNumber,
              t.track.durationMs,
              t.track.explicit,
              t.track.externalUrls,
              t.track.href,
              t.track.id,
              t.track.isPlayable,
              t.track.name,
              t.track.popularity,
              t.track.previewUrl,
              t.track.trackNumber,
              t.track.isLocal,
              t.track.uri
            )
          )
        }.toList
      }
    }
}
