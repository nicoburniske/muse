package muse.service

import muse.domain.common.EntityType
import muse.domain.session.UserSession
import muse.domain.spotify.{Album, Artist, Image, AuthCodeFlowData, Track, User, UserPlaylist}
import muse.domain.table.{AppUser, Review, ReviewComment}
import muse.service.persist.DatabaseOps
import muse.service.spotify.SpotifyService
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import zhttp.http.HttpError
import zio.*
import zio.json.*

import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

object RequestProcessor {
  type UserLoginEnv = DatabaseOps & SttpBackend[Task, Any]

  /**
   * Handles a user login.
   *
   * @param auth
   * current user auth data from spotify
   * @return
   * true if new User was created, false if current user was updated.
   */
  def handleUserLogin(auth: AuthCodeFlowData): ZIO[UserLoginEnv, Throwable, User] =
    for {
      spotify <- SpotifyService.live(auth.accessToken)
      userInfo <- spotify.getCurrentUserProfile
      res <- createOrUpdateUser(userInfo.id)
      resText = if (res) "Created" else "Updated"
      _ <-
        ZIO.logInfo(
          s"Successfully logged in ${userInfo.id}. $resText account. Access Token = ${auth.accessToken}")
    } yield userInfo

  /**
   * If username already exists, update existing row's auth information. Otherwise create user.
   *
   * @param appUser
   *   current user request
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def createOrUpdateUser(appUser: String): ZIO[DatabaseOps, SQLException, Boolean] = for {
    userRes <- DatabaseOps.getUserById(appUser)
    func     = if (userRes.nonEmpty) (u: String) => ZIO.succeed(u) else DatabaseOps.createUser
    _       <- func(appUser)
  } yield userRes.isEmpty

  enum ReviewOptions:
    case UserOwnedReviewsPublic, UserOwnedReviewsPrivate, UserOwnedReviewsAll, UserAccessReviews

  def userReviewsOptions(userId: String, options: ReviewOptions) = options match
    case ReviewOptions.UserOwnedReviewsPublic  =>
      DatabaseOps.getUserReviews(userId).map(_.filter(_.isPublic))
    case ReviewOptions.UserOwnedReviewsPrivate =>
      DatabaseOps.getUserReviews(userId).map(_.filterNot(_.isPublic))
    case ReviewOptions.UserOwnedReviewsAll     =>
      DatabaseOps.getUserReviews(userId)
    case ReviewOptions.UserAccessReviews       =>
      DatabaseOps.getAllUserReviews(userId)

}
