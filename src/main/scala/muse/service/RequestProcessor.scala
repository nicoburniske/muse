package muse.service

import muse.domain.common.EntityType
import muse.domain.create.{CreateComment, CreateReview}
import muse.domain.response.{
  DetailedAlbum,
  DetailedArtist,
  DetailedPlaylist,
  DetailedTrack,
  ReviewDetailed,
  ReviewEntity,
  ReviewSummary
}
import muse.domain.session.UserSession
import muse.domain.spotify.{Album, Artist, Image, InitialAuthData, Track, User, UserPlaylist}
import muse.domain.tables.{AppUser, Review, ReviewComment}
import muse.service.persist.DatabaseQueries
import muse.service.spotify.SpotifyService.*
import muse.service.spotify.{SpotifyAPI, SpotifyService}
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import zhttp.http.HttpError
import zio.*
import zio.json.*

import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

object RequestProcessor {
  type UserLoginEnv = SttpBackend[Task, Any] & DatabaseQueries

  /**
   * Handles a user login.
   *
   * @param auth
   *   current user auth data from spotify
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def handleUserLogin(auth: InitialAuthData): ZIO[UserLoginEnv, Throwable, User] =
    for {
      spotifyService <- SpotifyService.live(auth.accessToken)
      userInfo       <- spotifyService.getCurrentUserProfile
      res            <- createOrUpdateUser(userInfo.id)
      resText         = if (res) "Created" else "Updated"
      _              <-
        ZIO.logInfo(
          s"Successfully logged in ${userInfo.id}. ${resText} account. Access Token = ${auth.accessToken}")
    } yield userInfo

  def createReview(user: UserSession, review: CreateReview) = for {
    _ <- validateEntityOrDie(user.accessToken, review.entityId, review.entityType)
    _ <- DatabaseQueries.createReview(user.id, review)
  } yield ()

  def createReviewComment(user: UserSession, comment: CreateComment) = for {
    _ <- validateEntityOrDie(user.accessToken, comment.entityId, comment.entityType)
    _ <- DatabaseQueries.createReviewComment(user.id, comment)
  } yield ()

  def validateEntityOrDie(accessToken: String, entityId: String, entityType: EntityType) =
    validateEntity(accessToken, entityId, entityType).flatMap {
      case true  => ZIO.unit
      case false =>
        ZIO.fail(
          HttpError.BadRequest(s"Invalid Entity ID. ${entityType.toString} ${entityId} does not exist."))
    }

  def getUserInfo(accessToken: String) = for {
    spotifyService <- SpotifyService.live(accessToken)
    user           <- spotifyService.getCurrentUserProfile
  } yield user

  def validateEntity(accessToken: String, entityId: String, entityType: EntityType) = for {
    spotifyService <- SpotifyService.live(accessToken)
    res            <- spotifyService.isValidEntity(entityId, entityType)
  } yield res

  /**
   * If username already exists, update existing row's auth information. Otherwise create user.
   *
   * @param appUser
   *   current user request
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def createOrUpdateUser(appUser: String): ZIO[DatabaseQueries, SQLException, Boolean] = for {
    userRes <- DatabaseQueries.getUserById(appUser)
    func     = if (userRes.nonEmpty) (u: String) => ZIO.succeed(u) else DatabaseQueries.createUser
    _       <- func(appUser)
  } yield userRes.isEmpty

  enum ReviewOptions:
    case UserOwnedReviewsPublic, UserOwnedReviewsPrivate, UserOwnedReviewsAll, UserAccessReviews

  def userReviewsOptions(userId: String, options: ReviewOptions) = options match
    case ReviewOptions.UserOwnedReviewsPublic  =>
      DatabaseQueries.getUserReviews(userId).map(_.filter(_.isPublic))
    case ReviewOptions.UserOwnedReviewsPrivate =>
      DatabaseQueries.getUserReviews(userId).map(_.filterNot(_.isPublic))
    case ReviewOptions.UserOwnedReviewsAll     =>
      DatabaseQueries.getUserReviews(userId)
    case ReviewOptions.UserAccessReviews       =>
      DatabaseQueries.getAllUserReviews(userId)

  /**
   * Gets review sumamaries for the given user.
   *
   * @param userId
   *   user id
   * @param options
   *   the options for which reviews to retrieve
   * @return
   *   the ReviewSummaries
   */
  def getUserReviews(user: UserSession, options: ReviewOptions) = for {
    reviews      <- userReviewsOptions(user.id, options)
    groupedByType = reviews.groupMap(_.entityType)(_.entityId)
    token         = user.accessToken

    albumsRequests   = getAlbumsPar(token, groupedByType.getOrElse(EntityType.Album, Vector.empty))
    tracksRequest    = getTracksPar(token, groupedByType.getOrElse(EntityType.Track, Vector.empty))
    artistRequest    = getArtistsPar(token, groupedByType.getOrElse(EntityType.Artist, Vector.empty))
    playlistsRequest = getPlaylistsPar(token, groupedByType.getOrElse(EntityType.Playlist, Vector.empty))

    results                             <- albumsRequests <&> artistRequest <&> tracksRequest <&> playlistsRequest
    (albums, artists, tracks, playlists) = results

    // Group by entityId.
    // (ID, Name, Images)
    entities: Map[String, (String, String, List[Image])] =
      (albums.map(extractNameAndImages) ++
        artists.map(extractNameAndImages) ++
        tracks.map(extractNameAndImages) ++
        playlists.map(extractNameAndImages)).groupBy(_._1).view.mapValues(_.head).toMap

  } yield reviews.map { r =>
    val (_, name, images) = entities(r.entityId)
    ReviewSummary.fromReview(r, name, images)
  }

  // TODO: permission check.
  def getDetailedReview(session: UserSession, reviewIdString: String) = {
    val reviewId      = UUID.fromString(reviewIdString)
    val reviewOrError = DatabaseQueries
      .getReview(reviewId)
      .flatMap(_.fold(ZIO.fail(HttpError.BadRequest("Invalid Review Id")))(ZIO.succeed(_)))
    for {
      response          <- reviewOrError <&> DatabaseQueries.getReviewComments(reviewId)
      (review, comments) = response
      entity            <- getEntity(session.accessToken, review.entityId, review.entityType)
    } yield detailedReviewToJson(review, comments, entity)
  }

  // TODO: this is trash how can i fix it?
  def detailedReviewToJson(review: Review, comments: List[ReviewComment], r: ReviewEntity) =
    r match
      case d: DetailedAlbum    => ReviewDetailed(review, comments, d).toJsonPretty
      case d: DetailedTrack    => ReviewDetailed(review, comments, d).toJsonPretty
      case d: DetailedArtist   => ReviewDetailed(review, comments, d).toJsonPretty
      case d: DetailedPlaylist => ReviewDetailed(review, comments, d).toJsonPretty

  private def getEntity(accessToken: String, entityId: String, entityType: EntityType) =
    entityType match {
      case EntityType.Album    => SpotifyService.getDetailedAlbum(accessToken, entityId)
      case EntityType.Artist   => SpotifyService.getDetailedArtist(accessToken, entityId)
      case EntityType.Playlist => SpotifyService.getDetailedPlaylist(accessToken, entityId)
      case EntityType.Track    => SpotifyService.getDetailedTrack(accessToken, entityId)
    }

  def extractNameAndImages(e: Album | Artist | UserPlaylist | Track) =
    e match {
      case (a: Album)        => (a.id, a.name, a.images)
      case (a: Artist)       => (a.id, a.name, a.images.getOrElse(Nil))
      case (a: UserPlaylist) => (a.id, a.name, a.images)
      case (a: Track)        => (a.id, a.name, a.album.map(_.images).getOrElse(Nil))
    }

}
