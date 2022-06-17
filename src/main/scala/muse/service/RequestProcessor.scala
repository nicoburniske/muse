package muse.service

import zio.*
import sttp.client3.SttpBackend

import javax.sql.DataSource
import muse.domain.tables.AppUser
import muse.domain.spotify.{Album, Artist, Image, InitialAuthData, Track, User, UserPlaylist}
import muse.persist.DatabaseQueries
import muse.utils.Givens.given

import java.sql.SQLException
import muse.domain.common.EntityType
import muse.domain.create.{CreateComment, CreateReview}
import muse.domain.response.ReviewSummary
import muse.domain.session.UserSession
import muse.service.spotify.{SpotifyAPI, SpotifyService}
import zhttp.http.HttpError

object RequestProcessor {
  type UserLoginEnv = SttpBackend[Task, Throwable] & DatabaseQueries
  val XSESSION = "xsession"

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
      asTableRow      = AppUser(userInfo.id, auth.accessToken, auth.refreshToken)
      res            <- createOrUpdateUser(asTableRow)
      resText         = if (res) "Created" else "Updated"
      _              <-
        ZIO.logInfo(
          s"Successfully logged in ${userInfo.id}. ${resText} account. Access Token = ${auth.accessToken}")
    } yield userInfo

  def createReview(user: UserSession, review: CreateReview) = for {
    _ <- validateEntityOrDie(user, review.entityId, review.entityType)
    _ <- DatabaseQueries.createReview(user.id, review)
  } yield ()

  def createReviewComment(user: UserSession, comment: CreateComment) = for {
    _ <- validateEntityOrDie(user, comment.entityId, comment.entityType)
    _ <- DatabaseQueries.createReviewComment(user.id, comment)
  } yield ()

  def validateEntityOrDie(user: UserSession, entityId: String, entityType: EntityType) =
    validateEntity(user, entityId, entityType).flatMap {
      case true  => ZIO.unit
      case false =>
        ZIO.fail(
          HttpError.BadRequest(s"Invalid Entity ID. ${entityType.toString} ${entityId} does not exist."))
    }

  def validateEntity(user: UserSession, entityId: String, entityType: EntityType) = for {
    spotifyService <- SpotifyService.live(user.accessToken)
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
  def createOrUpdateUser(appUser: AppUser): ZIO[DatabaseQueries, SQLException, Boolean] = for {
    userRes <- DatabaseQueries.getUserById(appUser.id)
    func     = if (userRes.nonEmpty) DatabaseQueries.updateUser else DatabaseQueries.createUser
    _       <- func(appUser)
  } yield userRes.isEmpty

  enum ReviewOptions {
    case UserOwnedReviewsPublic, UserOwnedReviewsPrivate, UserOwnedReviewsAll, UserAccessReviews
  }

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
  def getUserReviews(userId: String, options: ReviewOptions) = for {
    reviews      <- userReviewsOptions(userId, options)
    groupedByType = reviews.groupMap(_.entityType)(_.entityId)

    albumsRequests   = getAlbumsPar(groupedByType.getOrElse(EntityType.Album, Vector.empty))
    tracksRequest    = getTracksPar(groupedByType.getOrElse(EntityType.Track, Vector.empty))
    artistRequest    = getArtistsPar(groupedByType.getOrElse(EntityType.Artist, Vector.empty))
    playlistsRequest = getPlaylistsPar(groupedByType.getOrElse(EntityType.Playlist, Vector.empty))
    results         <- albumsRequests <&> artistRequest <&> tracksRequest <&> playlistsRequest

    (albums, artists, tracks, playlists) = results

    // Group by entityId.
    entities: Map[String, IdNameImages] =
      (albums.map(extractNameAndImages) ++
        artists.map(extractNameAndImages) ++
        tracks.map(extractNameAndImages) ++
        playlists.map(extractNameAndImages)).groupBy(_._1).view.mapValues(_.head).toMap

  } yield reviews.map { r =>
    val (_, name, images) = entities(r.entityId)
    ReviewSummary.fromReview(r, name, images)
  }

  type IdNameImages = (String, String, List[Image])

  def extractNameAndImages(e: Album | Artist | UserPlaylist | Track): IdNameImages =
    e match {
      case (a: Album)        => (a.id, a.name, a.images)
      case (a: Artist)       => (a.id, a.name, a.images.getOrElse(Nil))
      case (a: UserPlaylist) => (a.id, a.name, a.images)
      // See how much detail is in each album. Could be missing stuff.
      case (a: Track)        => (a.id, a.name, a.album.map(_.images).getOrElse(Nil))
    }

  def getTracksPar(ids: Seq[String]) = {
    for {
      spotify <- ZIO.service[SpotifyAPI[Task]]
      res     <- parallelRequest(ids, 50, spotify.getTracks(_))
    } yield res
  }

  def getAlbumsPar(ids: Seq[String]) = for {
    spotify <- ZIO.service[SpotifyAPI[Task]]
    res     <- parallelRequest(ids, 20, spotify.getAlbums)
  } yield res

  def getArtistsPar(ids: Seq[String]) = for {
    spotify <- ZIO.service[SpotifyAPI[Task]]
    res     <- parallelRequest(ids, 50, spotify.getArtists)
  } yield res

  // This sucks. Might need to cache this.
  // Is different from the others because you can only get one playlist at a time.
  def getPlaylistsPar(ids: Seq[String]) =
    ZIO.service[SpotifyAPI[Task]].flatMap { spotify =>
      ZIO.foreachPar(ids.toVector)(id => spotify.getPlaylist(id))
    }

  def parallelRequest[I, R](
      ids: Seq[I],
      maxPerRequest: Int,
      singleRequest: Seq[I] => Task[Vector[R]]): ZIO[Any, Throwable, Vector[R]] = for {
    responses <- ZIO.foreachPar(ids.grouped(maxPerRequest).toVector)(singleRequest)
  } yield responses.flatten
}
