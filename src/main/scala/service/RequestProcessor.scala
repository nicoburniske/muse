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
import domain.spotify.Album
import domain.spotify.Artist
import domain.spotify.UserPlaylist
import domain.common.EntityType

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

  /**
   * Get's all the user reviews
   * @param userId
   * @param publicOnly
   * @return
   */
  def getUserReviews(userId: String, publicOnly: Boolean) = for {
    reviews      <- DatabaseQueries.getAllUserReviews(userId)
    groupedByType = reviews.groupMap(_.entityType)(_.entityId)
    groupedById   = reviews.groupBy(_.entityId)

    albumsRequests   = getAlbumsPar(groupedByType(EntityType.Album))
    tracksRequest    = getTracksPar(groupedByType(EntityType.Track))
    artistRequest    = getArtistsPar(groupedByType(EntityType.Track))
    playlistsRequest = getPlaylistsPar(groupedByType(EntityType.Playlist))
    results         <- albumsRequests <&> artistRequest <&> tracksRequest <&> playlistsRequest

    (albums, artists, tracks, playlists) = results

//    allReviewSummaries = Seq(
//      albums.map(a => ReviewSummary(a,_))
//    )

  } yield albums

  def getTracksPar(ids: Seq[String]): ZIO[SpotifyService[Task], Throwable, Vector[Track]] = {
    for {
      spotify <- ZIO.service[SpotifyService[Task]]
      res     <- parallelRequest(ids, 50, spotify.getTracks(_))
    } yield res
  }

  def getAlbumsPar(ids: Seq[String]): ZIO[SpotifyService[Task], Throwable, Vector[Album]] = for {
    spotify            <- ZIO.service[SpotifyService[Task]]
    res: Vector[Album] <- parallelRequest(ids, 20, spotify.getAlbums)
  } yield res

  def getArtistsPar(ids: Seq[String]): ZIO[SpotifyService[Task], Throwable, Vector[Artist]] = for {
    spotify <- ZIO.service[SpotifyService[Task]]
    res     <- parallelRequest(ids, 50, spotify.getArtists)
  } yield res

  // This sucks. Might need to cache this.
  // Is different from the others because you can only get one playlist at a time.
  def getPlaylistsPar(ids: Seq[String]): ZIO[SpotifyService[Task], Throwable, Vector[UserPlaylist]] =
    ZIO
      .service[SpotifyService[Task]]
      .flatMap { spotify => ZIO.foreachPar(ids)(id => spotify.getPlaylist(id)) }
      .flatMap { values =>
        val (errors, successes) = values.partitionMap(identity)
        if (errors.isEmpty)
          ZIO.succeed(successes.toVector)
        else
          ZIO.fail(MultiError(errors.toList))
      }

  // This is confusing? should this be unfailable ZIO?
  // def getThingsPar[I, R](ids: Seq[String], maxPerRequest: Int, singleRequest: Seq[String] => Task[SpotifyResponse[Seq[R]]]) = {
  //   for {
  //     spotify <- ZIO.service[SpotifyService[Task]]
  //     res <- parallelRequest(ids, 20, singleRequest)
  //   } yield res
  // }

  case class MultiError(errors: List[SpotifyRequestError]) extends Exception {
    override def getMessage = "Errors: " + errors.map(_.getMessage()).mkString(", ")
  }

  // TODO: Change return error type to only be MultiError?
  def parallelRequest[I, R](
      ids: Seq[I],
      maxPerRequest: Int,
      singleRequest: Seq[I] => Task[SpotifyResponse[Vector[R]]]): ZIO[Any, Throwable, Vector[R]] = {
    for {
      responses <- ZIO.foreachPar(ids.grouped(maxPerRequest).toVector)(singleRequest)
      res       <- {
        val (errors, successes) = responses.partitionMap(identity)
        if (errors.isEmpty)
          ZIO.succeed(successes.flatten)
        else
          ZIO.fail(MultiError(errors.toList))
      }
    } yield res
  }
}
