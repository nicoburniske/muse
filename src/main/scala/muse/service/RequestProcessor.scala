package muse.service

import zio.*
import sttp.client3.SttpBackend

import javax.sql.DataSource
import muse.domain.tables.AppUser
import muse.domain.spotify.{Album, Artist, AuthData, Image, Track, UserPlaylist}
import muse.service.SpotifyServiceLive
import muse.persist.DatabaseQueries
import muse.utils.Givens.given

import java.sql.SQLException
import muse.domain.common.EntityType
import muse.domain.response.ReviewSummary

object RequestProcessor {
  type UserLoginEnv = SttpBackend[Task, Any] & DatabaseQueries
  val XSESSION = "xsession"

  /**
   * Handles a user login.
   *
   * @param auth
   *   current user auth data from spotify
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def handleUserLogin(auth: AuthData): ZIO[UserLoginEnv, Throwable, AppUser] =
    for {
      backend       <- ZIO.service[SttpBackend[Task, Any]]
      spotifyService = SpotifyServiceLive[Task](backend, auth.accessToken, auth.refreshToken)
      userInfo      <- spotifyService.getCurrentUserProfile
      asTableRow     = AppUser(userInfo.id, auth.accessToken, auth.refreshToken)
      res           <- createOrUpdateUser(asTableRow)
      resText        = if (res) "Created" else "Updated"
      _             <-
        ZIO.logInfo(
          s"Successfully logged in ${userInfo.displayName}.${resText} account. Access Token = ${auth.accessToken}")
    } yield asTableRow

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

  def getTracksPar(ids: Seq[String]): ZIO[SpotifyServiceLive[Task], Throwable, Vector[Track]] = {
    for {
      spotify <- ZIO.service[SpotifyServiceLive[Task]]
      res     <- parallelRequest(ids, 50, spotify.getTracks(_))
    } yield res
  }

  def getAlbumsPar(ids: Seq[String]): ZIO[SpotifyServiceLive[Task], Throwable, Vector[Album]] = for {
    spotify <- ZIO.service[SpotifyServiceLive[Task]]
    res     <- parallelRequest(ids, 20, spotify.getAlbums)
  } yield res

  def getArtistsPar(ids: Seq[String]): ZIO[SpotifyServiceLive[Task], Throwable, Vector[Artist]] = for {
    spotify <- ZIO.service[SpotifyServiceLive[Task]]
    res     <- parallelRequest(ids, 50, spotify.getArtists)
  } yield res

  // This sucks. Might need to cache this.
  // Is different from the others because you can only get one playlist at a time.
  def getPlaylistsPar(ids: Seq[String]): ZIO[SpotifyServiceLive[Task], Throwable, Vector[UserPlaylist]] =
    ZIO.service[SpotifyServiceLive[Task]].flatMap { spotify =>
      ZIO.foreachPar(ids.toVector)(id => spotify.getPlaylist(id))
    }

  def parallelRequest[I, R](
      ids: Seq[I],
      maxPerRequest: Int,
      singleRequest: Seq[I] => Task[Vector[R]]): ZIO[Any, Throwable, Vector[R]] = for {
    responses <- ZIO.foreachPar(ids.grouped(maxPerRequest).toVector)(singleRequest)
  } yield responses.flatten
}
