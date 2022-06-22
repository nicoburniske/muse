package muse.server

import caliban.schema.{GenericSchema, Schema}
import caliban.wrappers.Wrappers.printErrors
import caliban.{GraphQL, RootResolver}
import muse.domain.common.EntityType
import muse.domain.spotify
import muse.domain.spotify.AlbumType
import muse.domain.tables.ReviewComment
import muse.service.persist.{DatabaseQueries, QuillContext}
import muse.service.spotify.{SpotifyAPI, SpotifyService}
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
  case class GetUser(id: String) extends Request[Nothing, User]

  def getUser(id: String)                                  = ZQuery.fromRequest(GetUser(id))(UserDataSource)
  val UserDataSource: DataSource[DatabaseQueries, GetUser] =
    DataSource.fromFunction("UserDataSource")(req => User(req.id, getUserReviews(req.id)))

  // wtf is the error in Request tied to?
  case class GetUserReviews(userId: String) extends Request[Nothing, List[Review]]

  def getUserReviews(userId: String)                                     = ZQuery.fromRequest(GetUserReviews(userId))(UserReviewsDataSource)
  val UserReviewsDataSource: DataSource[DatabaseQueries, GetUserReviews] =
    DataSource.fromFunctionZIO("UserReviewsDataSource") { req =>
      DatabaseQueries.getUserReviews(req.userId).map { value =>
        value.map { tReview =>
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
    }

  case class GetReview(reviewId: UUID) extends Request[Nothing, Option[Review]]

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

  def getReviewComments(reviewId: UUID)                                 = ZQuery.fromRequest(GetReviewComments(reviewId))(CommentDataSource)
  // This could be weird on second thought because of permissions.
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
    user: UserArgs => ZQuery[DatabaseQueries, Nothing, User],
    reviews: ReviewsArgs => ZQuery[DatabaseQueries, Nothing, Option[Review]])

object ApiGraphQL {
  given userSchema: Schema[DatabaseQueries & SpotifyAPI[Task], User] = Schema.gen

  given userArgs: Schema[DatabaseQueries, UserArgs] = Schema.gen

  given reviewSchema: Schema[DatabaseQueries & SpotifyAPI[Task], Review] = Schema.gen

  given commentsSchema: Schema[DatabaseQueries & SpotifyAPI[Task], Comment] = Schema.gen

  given entitySchema: Schema[SpotifyAPI[Task], ReviewEntity] = Schema.gen

  given albumSchema: Schema[SpotifyAPI[Task], Album] = Schema.gen

  given artistSchema: Schema[SpotifyAPI[Task], Artist] = Schema.gen

  given playlistSchema: Schema[SpotifyAPI[Task], Playlist] = Schema.gen

  given trackSchema: Schema[SpotifyAPI[Task], Track] = Schema.gen

  val resolver = Queries(id => Reqs.getUser(id.id), ids => Reqs.getReview(ids.id))

  val api =
    GraphQL.graphQL[DatabaseQueries & SpotifyAPI[Task], Queries, Unit, Unit](
      RootResolver(resolver)) @@ printErrors
}

// Responses
sealed trait Pagination

case object All extends Pagination

case class Offset(first: Int, from: Int) extends Pagination

final case class User(
    id: String,
    //    reviews: Pagination => ZQuery[DatabaseQueries, Nothing, List[Review]]
    reviews: ZQuery[DatabaseQueries, Nothing, List[Review]]
    // TODO: incorporate all spotify stuff.
)

final case class Review(
    id: UUID,
    createdAt: Instant,
    creatorId: String,
    reviewName: String,
    isPublic: Boolean,
    //    comments: Pagination => ZQuery[DatabaseQueries, Nothing, List[Comment]]
    comments: ZQuery[DatabaseQueries, Nothing, List[Comment]],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[SpotifyAPI[Task], Throwable, ReviewEntity]
)

final case class Comment(
    id: Int,
    reviewId: UUID,
    createdAt: Instant,
    updatedAt: Instant,
    // If none, then it is root comment.
    parentCommentId: Option[Int],
    commenterId: String,
    commenter: ZQuery[DatabaseQueries, Nothing, User],
    comment: Option[String],
    rating: Option[Int],
    entityId: String,
    entityType: EntityType,
    entity: ZQuery[EntityEnv, Throwable, ReviewEntity]
)

def getEntity(entityId: String, entityType: EntityType): ZQuery[EntityEnv, Throwable, ReviewEntity] =
  entityType match
    case EntityType.Album    => getAlbums(List(entityId)).map(_.head)
    case EntityType.Artist   => getArtists(List(entityId)).map(_.head)
    case EntityType.Playlist => getPlaylist(entityId)
    case EntityType.Track    => getTrack(entityId)

type EntityEnv = SpotifyAPI[Task]

case class GetAlbums(ids: List[String]) extends Request[Throwable, List[Album]]

def getAlbums(albumIds: List[String])                     = ZQuery.fromRequest(GetAlbums(albumIds))(AlbumDataSource)
val AlbumDataSource: DataSource[EntityEnv, GetAlbums]     = DataSource.fromFunctionZIO("AlbumDataSource") {
  reqs =>
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      spotify.getAlbums(reqs.ids).map { albums => albums.map(spotAlbumToAlbum).toList }
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
    getAlbums(List(t.album.get.id)).map(_.head),
    getArtists(t.artists.map(_.id)),
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
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      reqChunks.toList match
        case Nil         => ZIO.succeed(CompletedRequestMap.empty)
        case head :: Nil =>
          spotify
            .getTrack(head.id)
            .either
            .map {
              case Left(e)  =>
                CompletedRequestMap.empty.insert(head)(Left(e))
              case Right(t) =>
                CompletedRequestMap.empty.insert(head)(Right(spotifyTrackToTrack(t)))
            }
            .zipLeft(ZIO.logInfo(s"Requested single track ${head.id}"))
        case reqs        =>
          // TODO: make constants for max batch size.
          ZIO
            .foreachPar(reqs.grouped(50).toVector) { reqs =>
              spotify.getTracks(reqs.map(_.id)).either.map(reqs -> _)
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

case class GetArtists(ids: List[String]) extends Request[Throwable, List[Artist]]

def getArtists(artistIds: List[String])                 = ZQuery.fromRequest(GetArtists(artistIds))(ArtistDataSource)
val ArtistDataSource: DataSource[EntityEnv, GetArtists] = DataSource.fromFunctionZIO("ArtistDataSource") {
  reqs =>
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      spotify.getArtists(reqs.ids).map {
        _.map { a =>
          Artist(
            a.externalUrls,
            a.followers.get.total,
            a.genres.getOrElse(Nil),
            a.href,
            a.id,
            a.images.fold(Nil)(_.map(_.url)),
            a.name,
            a.popularity.get
          )
        }.toList
      }
    }
}

case class Artist(
    externalUrls: Map[String, String],
    numFollowers: Int,
    genres: List[String],
    href: String,
    id: String,
    images: List[String],
    name: String,
    popularity: Int
    //    albums: Pagination => ZQuery[Any, Nothing, List[Album]],
    //    topTracks: Pagination => ZQuery[Any, Nothing, List[Track]]
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
  DataSource.fromFunctionZIO("PlaylistDataSource") { reqs =>
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      spotify.getPlaylist(reqs.id).map { p =>
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
  }

//
case class GetPlaylistTracks(playlistId: String) extends Request[Throwable, List[PlaylistTrack]]
def getPlaylistTracks(playlistId: String)                             =
  ZQuery.fromRequest(GetPlaylistTracks(playlistId))(PlaylistTrackDataSource)
val PlaylistTrackDataSource: DataSource[EntityEnv, GetPlaylistTracks] =
  DataSource.fromFunctionZIO("PlaylistTrackDataSource") { reqs =>
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      spotify.getAllPlaylistTracks(reqs.playlistId).map {
        _.map { t =>
          PlaylistTrack(
            t.addedAt,
            SpotifyUser(t.addedBy.id, t.addedBy.href, t.addedBy.uri, t.addedBy.externalUrls),
            t.isLocal,
            Track(
              getAlbums(List(t.track.album.get.id)).map(_.head),
              getArtists(t.track.artists.map(_.id)),
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

final case class PlaylistTrack(
    addedAt: Instant,
    addedBy: SpotifyUser,
    isLocal: Boolean,
    track: Track
)

final case class SpotifyUser(id: String, href: String, uri: String, externalUrls: Map[String, String])
