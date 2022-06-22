package muse.server

import caliban.schema.{GenericSchema, Schema}
import caliban.wrappers.Wrappers.printErrors
import caliban.{GraphQL, RootResolver}
import muse.domain.common.EntityType
import muse.domain.session.UserSession
import muse.domain.spotify
import muse.domain.spotify.AlbumType
import muse.domain.tables.ReviewComment
import muse.server.MuseMiddleware.Auth
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyService
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.Console.printLine
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import scala.util.Try

// TODO: consider if ids or something simple should exist outside as field outside of sub-entity.

/**
 * Requests + DataSource
 */
object Reqs {
  // TODO create a check for if the given user exists and return an optional accordingly.
  case class GetUser(id: String) extends Request[Nothing, User]

  def getUser(id: String) = ZQuery.fromRequest(GetUser(id))(UserDataSource)

  val UserDataSource: DataSource[DatabaseQueries, GetUser] =
    DataSource.fromFunction("UserDataSource") { req => User(req.id, getUserReviews(req.id)) }

  // TODO incorporate
  case class GetUserReviews(userId: String) extends Request[SQLException, List[Review]]

  def getUserReviews(userId: String) = ZQuery.fromRequest(GetUserReviews(userId))(UserReviewsDataSource)

  val UserReviewsDataSource: DataSource[DatabaseQueries, GetUserReviews] =
    DataSource.fromFunctionZIO("UserReviewsDataSource") { req =>
      for {
        reviews <- DatabaseQueries.getUserReviews(req.userId)
      } yield reviews.map { tReview =>
        Review(
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
  def getReview(reviewId: UUID)                                = ZQuery.fromRequest(GetReview(reviewId))(ReviewDataSource)
  val ReviewDataSource: DataSource[DatabaseQueries, GetReview] =
    DataSource.fromFunctionZIO("ReviewDataSource") { g =>
      DatabaseQueries.getReview(g.reviewId).map {
        _.map { r =>
          Review(
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
          for {
            maybeComments <- DatabaseQueries.getReviewComments(req.reviewId).either
            _             <- ZIO.logInfo(s"Retrieved review ${req.reviewId.toString}")
          } yield {
            maybeComments match
              case Left(error)  => CompletedRequestMap.empty.insert(req)(Left(error))
              case Right(value) =>
                val asComments = value.map(tableCommentToComment)
                CompletedRequestMap.empty.insert[SQLException, List[Comment]](req)(Right(asComments))
          }
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
    Comment(
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
}

case class UserArgs(id: String)
case class ReviewsArgs(id: UUID)
case class Queries(
    user: UserArgs => ZQuery[DatabaseQueries, Throwable, User],
    reviews: ReviewsArgs => ZQuery[DatabaseQueries, Throwable, Option[Review]])

object ApiGraphQL {
  given userSchema: Schema[DatabaseQueries & EntityEnv, User] = Schema.gen

  given userArgs: Schema[DatabaseQueries, UserArgs] = Schema.gen

  given reviewSchema: Schema[DatabaseQueries & EntityEnv, Review] = Schema.gen

  given commentsSchema: Schema[DatabaseQueries & EntityEnv, Comment] = Schema.gen

  given entitySchema: Schema[EntityEnv, ReviewEntity] = Schema.gen

  given albumSchema: Schema[EntityEnv, Album] = Schema.gen

  given artistSchema: Schema[EntityEnv, Artist] = Schema.gen

  given playlistSchema: Schema[EntityEnv, Playlist] = Schema.gen

  given trackSchema: Schema[EntityEnv, Track] = Schema.gen

  val resolver = Queries(id => Reqs.getUser(id.id), ids => Reqs.getReview(ids.id))

  val api =
    GraphQL.graphQL[DatabaseQueries & EntityEnv, Queries, Unit, Unit](RootResolver(resolver)) @@ printErrors
}

// TODO: incorporate pagination.
sealed trait Pagination

case object All extends Pagination

case class Offset(first: Int, from: Int) extends Pagination

final case class User(
    id: String,
    //    reviews: Pagination => ZQuery[DatabaseQueries, Nothing, List[Review]]
    reviews: ZQuery[DatabaseQueries, Throwable, List[Review]]
    // TODO: incorporate all spotify stuff.
)

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    //    comments: Pagination => ZQuery[DatabaseQueries, Nothing, List[Comment]]
    comments: ZQuery[DatabaseQueries, Throwable, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[EntityEnv, Throwable, ReviewEntity]
)

final case class Comment(
    id: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenterId: String,
    commenter: ZQuery[DatabaseQueries, Throwable, User],
    comment: Option[String],
    rating: Option[Int],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[EntityEnv, Throwable, ReviewEntity]
)

def getEntity(entityId: String, entityType: EntityType): ZQuery[EntityEnv, Throwable, ReviewEntity] =
  entityType match
    case EntityType.Album    => getAlbum(entityId)
    case EntityType.Artist   => getArtist(entityId)
    case EntityType.Playlist => getPlaylist(entityId)
    case EntityType.Track    => getTrack(entityId)

type EntityEnv = SpotifyService

case class GetAlbum(id: String) extends Request[Throwable, Album]

def getAlbum(albumId: String)                             = ZQuery.fromRequest(GetAlbum(albumId))(AlbumDataSource)
val AlbumDataSource: DataSource[EntityEnv, GetAlbum]      =
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
    a.albumType.toString,
    a.externalUrls,
    a.genres.getOrElse(Nil),
    a.id,
    a.images.map(_.url),
    a.label,
    a.name,
    a.popularity,
    a.releaseDate)

sealed trait ReviewEntity

case class Album(
    albumGroup: Option[String],
    albumType: String,
    externalUrls: Map[String, String],
    genres: List[String],
    id: String,
    images: List[String],
    label: Option[String],
    name: String,
    popularity: Option[Int],
    releaseDate: String
    //    artists: ZQuery[Any, Nothing, List[Artist]]
    //    tracks: Pagination => ZQuery[Any, Nothing, List[Track]]
    //    tracks: ZQuery[Any, Nothing, List[Track]]
) extends ReviewEntity

////
// TODO change this to be BatchedDatasource
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

def getTrack(trackId: String)                        = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)
val TrackDataSource: DataSource[EntityEnv, GetTrack] =
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

case class Track(
    album: ZQuery[EntityEnv, Throwable, Album],
    artists: ZQuery[EntityEnv, Throwable, List[Artist]],
    //    artists: ZQuery[Any, Nothing, List[Artist]],
    discNumber: Int,
    durationMs: Int,
    explicit: Boolean,
    externalUrls: Map[String, String],
    href: String,
    id: String,
    isPlayable: Option[Boolean],
    name: String,
    popularity: Option[Int],
    previewUrl: Option[String],
    trackNumber: Int,
    isLocal: Boolean,
    uri: String
) extends ReviewEntity
//

case class GetArtist(id: String) extends Request[Throwable, Artist]

def getArtist(artistId: String)                        = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)
val ArtistDataSource: DataSource[EntityEnv, GetArtist] = DataSource.Batched.make("ArtistDataSource") { reqs =>
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
  Artist(
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

case class Artist(
    externalUrls: Map[String, String],
    numFollowers: Int,
    genres: List[String],
    href: String,
    id: String,
    images: List[String],
    name: String,
    popularity: Int,
    // TODO: pagination.
    albums: ZQuery[EntityEnv, Throwable, List[Album]],
    topTracks: ZQuery[EntityEnv, Throwable, List[Track]]
) extends ReviewEntity

// TODO: link back to user?
case class Playlist(
    collaborative: Boolean,
    description: String,
    externalUrls: Map[String, String],
    id: String,
    images: List[String],
    name: String,
    owner: SpotifyUser,
    primaryColor: Option[String],
    public: Boolean,
    tracks: ZQuery[EntityEnv, Throwable, List[PlaylistTrack]]
    // tracks: Pagination => ZQuery[EntityEnv, Throwable, List[PlaylistTrack]]
) extends ReviewEntity

case class GetPlaylist(id: String) extends Request[Throwable, Playlist]
def getPlaylist(playlistId: String)                        = ZQuery.fromRequest(GetPlaylist(playlistId))(PlaylistDataSource)
val PlaylistDataSource: DataSource[EntityEnv, GetPlaylist] =
  DataSource.fromFunctionZIO("PlaylistDataSource") { req =>
    SpotifyService.getPlaylist(req.id).map { p =>
      Playlist(
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
def getPlaylistTracks(playlistId: String)                             =
  ZQuery.fromRequest(GetPlaylistTracks(playlistId))(PlaylistTrackDataSource)
val PlaylistTrackDataSource: DataSource[EntityEnv, GetPlaylistTracks] =
  DataSource.fromFunctionZIO("PlaylistTrackDataSource") { reqs =>
    SpotifyService.getAllPlaylistTracks(reqs.playlistId).map {
      _.map { t =>
        PlaylistTrack(
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

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: SpotifyUser,
    isLocal: Boolean,
    track: Track
)

final case class SpotifyUser(id: String, href: String, uri: String, externalUrls: Map[String, String])
