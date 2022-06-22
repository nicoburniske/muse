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
import zio.Duration.*
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
      DatabaseQueries.getUserReviews(req.userId).map(_.map(Review.fromTable))
    }

  case class GetReview(reviewId: UUID) extends Request[SQLException, Option[Review]]

  def getReview(reviewId: UUID) = ZQuery.fromRequest(GetReview(reviewId))(ReviewDataSource)

  val ReviewDataSource: DataSource[DatabaseQueries, GetReview] =
    DataSource.fromFunctionZIO("ReviewDataSource") { g =>
      DatabaseQueries.getReview(g.reviewId).map {
        _.map(Review.fromTable)
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
              comments => CompletedRequestMap.empty.insert(req)(Right(comments.map(Comment.fromTable)))
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
                    grouped.getOrElse(r.reviewId, Nil).map(Comment.fromTable)
                  map.insert(r)(Right(asComments))
                }
          }
    }

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
              a => CompletedRequestMap.empty.insert(head)(Right(Album.fromSpotify(a))))
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
                    case Right(albums)   =>
                      val grouped = albums.map(Album.fromSpotify).groupBy(_.id).view.mapValues(_.head)
                      reqs.foldLeft(map) { (map, req) =>
                        val result =
                          grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Album)))(Right(_))
                        map.insert(req)(result)
                      }
              }
            }
            .zipLeft(ZIO.logInfo(s"Requested ${reqs.size} tracks in parallel"))
    }

  case class NotFoundError(entityId: String, entityType: EntityType) extends Throwable

  def getAlbumTracks(
      albumId: String,
      numTracks: Option[Int]): ZQuery[SpotifyService, Throwable, List[Track]] = ZQuery.fromZIO(
    addTimeLog("Retrieved album tracks")(
      numTracks match {
        case Some(total) =>
          ZIO
            .foreachPar((0 until total).grouped(50).map(_.start).toList) { r =>
              SpotifyService.getSomeAlbumTracks(albumId, Some(50), Some(r))
            }
            .map(_.flatMap(_.items).toList)
            .map(_.map(t => Track.fromSpotify(t, Some(albumId))))
        case None        =>
          SpotifyService
            .getAllAlbumTracks(albumId)
            .map(_.map(t => Track.fromSpotify(t, Some(albumId))).toList)
      }
    ))

  case class GetTrack(id: String) extends Request[Throwable, Track]

  def getTrack(trackId: String) = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)

  val TrackDataSource: DataSource[SpotifyService, GetTrack] =
    DataSource.Batched.make("TrackDataSource") { reqChunks =>
      reqChunks.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          addTimeLog("Retrieved Single Track")(
            SpotifyService
              .getTrack(head.id)
              .fold(
                e => CompletedRequestMap.empty.insert(head)(Left(e)),
                t => CompletedRequestMap.empty.insert(head)(Right(Track.fromSpotify(_)))
              )
          )
        case reqs        =>
          // TODO: make constants for max batch size.
          addTimeLog("Retrieved Multiple Tracks")(
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
                        val grouped =
                          tracks.map(t => Track.fromSpotify(t)).groupBy(_.id).view.mapValues(_.head)
                        reqs.foldLeft(map) { (map, req) =>
                          val result: Either[NotFoundError, Track] =
                            grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Track)))(Right(_))
                          map.insert(req)(result)
                        }
                }
              }
          )
    }

  case class GetArtist(id: String) extends Request[Throwable, Artist]

  def getArtist(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  val ArtistDataSource: DataSource[SpotifyService, GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      reqs.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          addTimeLog("Retrieved Single Artist")(
            SpotifyService
              .getArtist(head.id)
              .fold(
                e => CompletedRequestMap.empty.insert(head)(Left(e)),
                a => CompletedRequestMap.empty.insert(head)(Right(Artist.fromSpotify(a))))
          )
        case _           =>
          addTimeLog("Retrieved mulitple Artists")(
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
                        val grouped = tracks.map(Artist.fromSpotify).groupBy(_.id).view.mapValues(_.head)
                        reqs.foldLeft(map) { (map, req) =>
                          val result =
                            grouped.get(req.id).fold(Left(NotFoundError(req.id, EntityType.Artist)))(Right(_))
                          map.insert(req)(result)
                        }
                }
              })
    }

  // TODO: maybe think of grouping requests by artist? Are we guaranteed to not have duplicates?
  case class GetArtistAlbums(artistId: String) extends Request[Throwable, List[Album]]

  def getArtistAlbums(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getAllArtistAlbums(artistId)).map(_.map(Album.fromSpotify).toList)

  // TODO: incorporate region/country.
  case class GetArtistTopTracks(artistId: String) extends Request[Throwable, List[Track]]

  def getArtistTopTracks(artistId: String) =
    ZQuery.fromZIO(SpotifyService.getArtistTopTracks(artistId)).map(_.map(Track.fromSpotify(_)).toList)

  // TODO: link back to user?

  case class GetPlaylist(id: String) extends Request[Throwable, Playlist]

  def getPlaylist(playlistId: String) = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)

  val PlaylistDataSource: DataSource[SpotifyService, GetPlaylist] =
    DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
      addTimeLog("Retrieved Playlist")(SpotifyService.getPlaylist(req.id).map(Playlist.fromSpotify))
    }

  case class GetPlaylistTracks(playlistId: String, numTracks: Int)
      extends Request[Throwable, List[PlaylistTrack]]

  def getPlaylistTracks(playlistId: String, numTracks: Int) =
    ZQuery.fromRequest(GetPlaylistTracks(playlistId, numTracks))(PlaylistTrackDataSource)

  val PlaylistTrackDataSource: DataSource[SpotifyService, GetPlaylistTracks] =
    DataSource.fromFunctionZIO("PlaylistTrackDataSource") { req =>
      addTimeLog("Retrieved all playlist tracks") {
        ZIO
          .foreachPar((0 until req.numTracks).grouped(100).map(_.start).toList) { r =>
            SpotifyService.getSomePlaylistTracks(req.playlistId, 100, Some(r))
          }
          .map(_.flatMap(_.items).toList)
          .map(_.map(PlaylistTrack.fromSpotify))
      }
    }

  def addTimeLog[R, E, A](message: String)(z: ZIO[R, E, A]): ZIO[R, E, A] =
    z.timed.flatMap { case (d, r) => ZIO.logInfo(s"$message in ${d.toMillis}ms").as(r) }
}
