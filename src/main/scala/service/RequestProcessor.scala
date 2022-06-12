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
import domain.spotify.Track

object RequestProcessor {

  /**
   * Handles a user login.
   *
   * @param auth
   *   current user auth data from spotify
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def handleUserLogin(auth: AuthData): ZIO[SttpBackend[Task, Any] & DatabaseQueries, Throwable, Boolean] =
    for {
      backend       <- ZIO.service[SttpBackend[Task, Any]]
      spotifyService = SpotifyService[Task](backend, auth.accessToken, auth.refreshToken)
      maybeUserInfo <- spotifyService.getCurrentUserProfile
      userInfo      <- ZIO.fromEither(maybeUserInfo)
      asTableRow     = AppUser(userInfo.id, auth.accessToken, auth.refreshToken)
      res           <- createOrUpdateUser(asTableRow)
      _             <- ZIO.logInfo(s"Successfully logged in ${userInfo.displayName}. Access Token = ${auth.accessToken}")
    } yield res

  /**
   * If username already exists, update existing row's auth information. Otherwise create user.
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

  // def getUserReviews(userId: String): ZIO[DatabaseQueries, SQLException, List[UserReview]]  = for {
  //   reviews <- DatabaseQueries.getAllUserReviews(userId)
  //   val grouped = reviews.groupBy(_.entityType)
  //   val albumRequests = grouped
  // }

  // TODO: getAlbumsPar, getArtistsPar, getPlaylistsPar
  def getTracksPar(ids: Seq[String]): ZIO[SpotifyService[Task], Throwable, Either[SpotifyRequestError, Vector[Track]]] = {
    for {
      spotify <- ZIO.service[SpotifyService[Task]]
      res <- parallelRequest(ids, 50, spotify.getTracks(_))
    } yield res
  }

  // TODO: Change return error type to only be MultiError 
  def parallelRequest[I, R](
      ids: Seq[I],
      maxPerRequest: Int,
      singleRequest: Seq[I] => Task[SpotifyResponse[Seq[R]]]) = {
    for {
      values <- ZIO.foreachPar(ids.grouped(maxPerRequest).toVector)(singleRequest)
    } yield {
      val (errors, successes) = values.partitionMap(identity)
      if (errors.isEmpty) Right(successes.flatten)
      else
        Left(SpotifyRequestError.MultiError(errors.toList))
    }
  }
}
