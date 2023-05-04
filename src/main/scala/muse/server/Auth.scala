package muse.server

import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.common.Types.{RefreshToken, SessionId, UserId}
import muse.domain.session.UserSession
import muse.domain.spotify.auth.AuthCodeFlowData
import muse.domain.table.User
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import muse.service.cache.RedisService
import sttp.client3.SttpBackend
import zio.http.model.{Cookie, HttpError, Method, Scheme}
import zio.http.*
import zio.json.*
import zio.{Cause, Chunk, Layer, Random, Ref, Schedule, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer, durationInt}

object Auth {

  val loginEndpoints = Http
    .collectZIO[Request] {
      case request @ Method.GET -> !! / "login" =>
        val redirectTo = request.url.queryParams.get("redirect").flatMap(_.headOption)
        generateRedirectUrl(redirectTo).mapBoth(
          e => Response.fromHttpError(HttpError.InternalServerError("Failed to generate redirect url.", Some(e))),
          url => Response.redirect(url.encode, false))
      case req @ Method.GET -> !! / "callback"  =>
        val queryParams = req.url.queryParams
        val code        = queryParams.get("code").flatMap(_.headOption)
        val state       = queryParams.get("state").flatMap(_.headOption)
        code -> state match {
          case (None, _)                 =>
            ZIO.fail(Response.fromHttpError(HttpError.BadRequest("Missing 'code' query parameter")))
          case (_, None)                 =>
            ZIO.fail(Response.fromHttpError(HttpError.BadRequest("Missing 'state' query parameter")))
          case (Some(code), Some(state)) =>
            {
              for {
                redirect     <- getRedirectFromState(state)
                newSessionId <- handleUserLogin(code)
                config       <- ZIO.service[ServerConfig]
                _            <- ZIO.logInfo(s"Successfully added session.")
              } yield {
                val cookie = Cookie(
                  COOKIE_KEY,
                  newSessionId,
                  isSecure = true,
                  isHttpOnly = true,
                  maxAge = Some(365.days.toSeconds),
                  // On localhost dev, we don't want a cookie domain.
                  domain = config.domain
                )
                Response.redirect(redirect).addCookie(cookie)
              }
              // TODO: this seems a little weird. Revise.
              // Success channel can be responses that are failures.
              // Errors are only server errors.
            }.catchSome { case r: Response => ZIO.succeed(r) }
              .tapErrorCause(cause => ZIO.logErrorCause("Failed to login user.", cause))
              .mapError {
                case t: Throwable => Response.fromHttpError(HttpError.InternalServerError("Failed to login user.", Some(t)))
              }
        }
    }

  // @@ csrfGenerate() // TODO: get this working?
  val sessionEndpoints = Http.collectZIO[Request] {
    case Method.POST -> !! / "logout" =>
      for {
        session <- RequestSession.get[UserSession]
        _       <- UserSessions.deleteUserSession(session.sessionId)
        _       <- ZIO.logInfo(s"Successfully logged out user ${session.userId}")
      } yield Response.ok
    case Method.GET -> !! / "session" =>
      for {
        session <- RequestSession.get[UserSession]
      } yield Response.text(session.sessionId)
    case Method.GET -> !! / "token"   =>
      // Guaranteed to have a valid access token for next 60 min.
      for {
        session     <- RequestSession.get[UserSession]
        accessToken <- UserSessions.getFreshAccessToken(session.sessionId)
      } yield Response.text(accessToken)
  }

  private val retrySchedule = Schedule.exponential(10.millis).jittered && Schedule.recurs(4)

  private def getRedirectFromState(state: String): ZIO[RedisService, RedisService.Error | Response, String] =
    RedisService
      .get[String, String](state).retry(retrySchedule).zipLeft(RedisService.delete(state).ignore)
      .someOrFail(Response.fromHttpError(HttpError.BadRequest("Invalid 'state' query parameter")))

  def generateRedirectUrl(redirectMaybe: Option[String]) = for {
    spotifyConfig <- ZIO.service[SpotifyConfig]
    serverConfig  <- ZIO.service[ServerConfig]
    state         <- Random.nextUUID.map(_.toString.take(30))
    redirect       = redirectMaybe
                       .filter(r => URL.fromString(r).isRight)
                       .getOrElse(serverConfig.frontendUrl)
    _             <- RedisService.set(state, redirect, Some(10.seconds)).retry(retrySchedule)
  } yield URL(
    Path.decode("/authorize"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443),
    QueryParams(
      "response_type" -> Chunk("code"),
      "client_id"     -> Chunk(spotifyConfig.clientID),
      "redirect_uri"  -> Chunk(spotifyConfig.redirectURI),
      "scope"         -> Chunk(scopes),
      "state"         -> Chunk(state)
    )
  )

  /**
   * Handles a user login.
   *
   * @param code
   *   Auth code from Spotify.
   *
   * @return
   *   the new session id.
   */
  def handleUserLogin(code: String) =
    for {
      auth        <- SpotifyAuthService.getAuthCode(code).retry(retrySchedule)
      spotify     <- SpotifyService.live(auth.accessToken)
      userProfile <- spotify.getCurrentUserProfile.retry(retrySchedule)
      userId       = userProfile.id
      _           <- ZIO
                       .fail(Response.fromHttpError(HttpError.BadRequest(s"User $userId is not a premium subscriber.")))
                       .when(!userProfile.product.contains("premium"))

      newSessionId <- Random.nextUUID.map(_.toString).map(SessionId(_))
      _            <- DatabaseService
                        .createOrUpdateUser(newSessionId, RefreshToken(auth.refreshToken), UserId(userId))
                        .tapErrorCause(e => ZIO.logErrorCause(s"Failed to process user $userId login.", e))
                        .zipLeft(ZIO.logInfo(s"Successfully logged in $userId."))
    } yield newSessionId

  val scopes = List(
    /**
     * Playback state.
     */

    // So users can change their playback state from the Muse's integrated player.
    "user-modify-playback-state",
    // So users can see their playback state from the Muse's integrated player.
    "user-read-playback-state",
    // So users can see their currently playing track from the Muse's integrated player.
    "user-read-currently-playing",

    /**
     * Library.
     */

    // So users can see their liked status for songs.
    "user-library-read",
    // So users can save/unsave tracks.
    "user-library-modify",

    /**
     * Playlist permissions.
     */

    // So users can review their private playlists.
    "playlist-read-private",
    // So users can review their collaborative playlists.
    "playlist-read-collaborative",
    // So users can add/remove/reorder tracks from their public playlists.
    "playlist-modify-public",
    // So users can add/remove/reorder tracks from their private playlists.
    "playlist-modify-private",

    // So music can be streamed to Muse's integrated player.
    "streaming",
    // To ensure users have premium subscriptions.
    "user-read-private"
  ).mkString(" ")
}
