package service

import zio.*
import sttp.client3.SttpBackend

import javax.sql.DataSource
import domain.tables.AppUser
import domain.spotify.AuthData
import service.SpotifyService
import persist.DatabaseQueries
import utils.Givens.given

import java.sql.SQLException

object RequestProcessor {

  /**
   * Handles a user login.
   *
   * @param auth
   * @return
   */
  def handleUserLogin(auth: AuthData): ZIO[SttpBackend[Task, Any] & DatabaseQueries, Throwable, Boolean] =
    for {
      backend       <- ZIO.service[SttpBackend[Task, Any]]
      spotifyService = SpotifyService[Task](backend, auth.accessToken, auth.refreshToken)
      maybeUserInfo <- spotifyService.getCurrentUserProfile
      userInfo      <- ZIO.fromEither(maybeUserInfo)
      asTableRow     = AppUser(userInfo.id, auth.accessToken, auth.refreshToken)
      res           <- createOrUpdateUser(asTableRow)
    } yield res

  /**
   * If username already exists, update existing row's auth information. Otherwise create user.
   * @param appUser
   *   current user request
   * @return
   *   true if new User was created, false if current user was created.
   */
  def createOrUpdateUser(appUser: AppUser): ZIO[DatabaseQueries, SQLException, Boolean] = for {
    userRes <- DatabaseQueries.getUserById(appUser.id)
    func     = if (userRes.nonEmpty) DatabaseQueries.updateUser else DatabaseQueries.createUser
    _       <- func(appUser)
  } yield userRes.isEmpty

  // create something from a cookie for a user?
}
