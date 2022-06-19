//package muse.graphql
//
//import muse.domain.spotify.AlbumType
//import muse.service.persist.DatabaseQueries
//import zio.Chunk
//import zio.*
//import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}
//
//import java.time.Instant
//import java.util.UUID
//
//// TODO: consider if ids or something simple should exist outside as field outside of sub-entity.
//
///**
// * Requests + DataSource
// */
//object Reqs {
//  case class GetUser(id: String) extends Request[Nothing, User]
//
//  val userDataSource: DataSource[DatabaseQueries, GetUser] =
//    DataSource.Batched.make("UserDataSource") { (requests: Chunk[GetUser]) =>
//      requests.toList match
//        case first :: Nil => ???
//        case reqs         => ???
//    }
//
//    def getUser(id: String) = ZQuery.fromRequest(GetUser(id))(userDataSource)
//
//  case class GetReview(id: UUID) extends Request[Nothing, Review]
//
//  val reviewDataSource: DataSource[DatabaseQueries, GetReview] =
//    DataSource.Batched.make("ReviewDataSource") { (requests: Chunk[GetReview]) =>
//      requests.toList match
//        case head :: Nil =>
//          for {
//            review <- DatabaseQueries.getReview(head.id).map(_.get)
//          } yield ()
//          ???
//        case reqs        => ???
//    }
//
//    case class GetReviewComments(id: UUID) extends Request[Nothing, List[Comment]]
//    val commentDataSource: DataSource[DatabaseQueries, GetReviewComments] =
//      DataSource.Batched.make("ReviewCommentsDataSource") { (requests: Chunk[GetReviewComments]) =>
//        requests.toList match
//          case head :: Nil =>
//            for {
//              comments <- DatabaseQueries.getReviewComments(head.id)
//              _        <- ZIO.logInfo(s"Retrieved review ${head.id.toString}")
//            } yield {
//              val asComments =
//                comments.map { r =>
//                  Comment(
//                    r.id,
//                    r.reviewId,
//                    r.createdAt,
//                    r.updatedAt,
//                    r.parentCommentId,
//                    getUser(r.commenter),
//                    r.comment,
//                    r.rating)
//                }
//              CompletedRequestMap.empty.insert(head)(Right(comments))
//            }
//          case reqs        => ???
//      }
//}
//// Responses
//sealed trait Pagination:
//  case object All                          extends Pagination
//  case class Offset(first: Int, from: Int) extends Pagination
//
//final case class User(
//    id: String,
//    displayName: Option[String],
//    reviews: Pagination => ZQuery[Any, Nothing, List[Review]]
//)
//
//final case class Review(
//    id: UUID,
//    createdAt: Instant,
//    creatorId: String,
//    reviewName: String,
//    isPublic: Boolean,
//    comments: Pagination => ZQuery[Any, Nothing, List[Comment]]
//    // ,entity: ZQuery[Any, Nothing, ReviewEntity]
//)
//
//final case class Comment(
//    id: Int,
//    reviewId: UUID,
//    createdAt: Instant,
//    updatedAt: Instant,
//    // If none, then it is root comment.
//    parentCommentId: Option[Int],
//    // Should the user comment ID be available by default? Should there be a parameter here?
//    commenter: ZQuery[Any, Nothing, User],
//    comment: Option[String],
//    rating: Option[Int]
//    // , entity: ZQuery[Any, Nothing, User]
//)
//
//sealed trait ReviewEntity
//
//case class Album(
//    albumGroup: Option[String],
//    albumType: AlbumType,
//    artists: ZQuery[Any, Nothing, List[Artist]],
//    externalUrls: Map[String, String],
//    genres: List[String],
//    id: String,
//    // Sort them largest to smallest?
//    images: List[String],
//    label: Option[String],
//    name: String,
//    popularity: Option[Int],
//    releaseDate: String,
//    tracks: Pagination => ZQuery[Any, Nothing, List[Track]]
//) extends ReviewEntity
////
//case class Track(
//    album: ZQuery[Any, Nothing, Album],
//    artists: ZQuery[Any, Nothing, List[Artist]],
//    discNumber: Int,
//    durationMs: Int,
//    explicit: Boolean,
//    externalUrls: Map[String, String],
//    id: String,
//    isPlayable: Boolean,
//    name: String,
//    popularity: Int,
//    previewUrl: String,
//    trackNumber: Int,
//    isLocal: Boolean
//) extends ReviewEntity
//
//case class Artist(
//    externalUrls: Map[String, String],
//    numFollowers: Int,
//    genres: List[String],
//    href: String,
//    id: String,
//    images: List[String],
//    name: String,
//    popularity: Int,
//    albums: Pagination => ZQuery[Any, Nothing, List[Album]],
//    topTracks: Pagination => ZQuery[Any, Nothing, List[Track]]
//) extends ReviewEntity
//
//case class Playlist(
//    collaborative: Boolean,
//    description: String,
//    externalUrls: Map[String, String],
//    id: String,
//    images: List[String],
//    name: String,
//    owner: User,
//    primaryColor: Option[String],
//    public: Boolean,
//    tracks: Pagination => ZQuery[Any, Nothing, List[PlaylistTrack]]
//) extends ReviewEntity
//
//final case class PlaylistTrack(
//    addedAt: Instant,
//    addedBy: User,
//    isLocal: Boolean,
//    track: ZQuery[Any, Nothing, Track]
//)
