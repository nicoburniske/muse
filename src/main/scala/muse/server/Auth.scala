package muse.server

import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.common.Types.{RefreshToken, SessionId, UserId}
import muse.domain.session.UserSession
import muse.domain.spotify.auth.AuthCodeFlowData
import muse.domain.table.User
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import sttp.client3.SttpBackend
import zio.http.{Http, Request, Response}
import zio.http.model.{Cookie, HttpError, Method, Scheme}
import zio.http.*
import zio.json.*
import zio.{Cause, Chunk, Layer, Random, Ref, Schedule, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer, durationInt}

object Auth {
  val scopes = List(
    "user-library-read",
    // To ensure premium subscription.
    "user-read-private",
    // Playlist permissions.
    "playlist-read-private",
    "playlist-read-collaborative",
    // playback state.
    "user-modify-playback-state",
    "user-read-playback-state",
    "user-read-currently-playing",
    // Library.
    "user-library-modify",
    // Streaming permissions!
    "streaming",
    // Playlist editing permissions!
    "playlist-modify-public",
    "playlist-modify-private"
  ).mkString(" ")

  val loginEndpoints = Http
    .collectZIO[Request] {
      case Method.GET -> !! / "login"          =>
        generateRedirectUrl
          .tap(url => ZIO.logInfo(s"Redirecting to ${url.encode}"))
          .map(url => Response.redirect(url.encode, false))
      case req @ Method.GET -> !! / "callback" =>
        req
          .url
          .queryParams
          .get("code")
          .flatMap(_.headOption)
          .fold {
            ZIO.fail(Response.fromHttpError(HttpError.BadRequest("Missing 'code' query parameter")))
          } { code =>
            {
              for {
                authData     <- SpotifyAuthService.getAuthCode(code)
                _            <- ZIO.logInfo("Received auth data for login")
                newSessionId <- handleUserLogin(authData)
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
                Response.redirect(config.frontendUrl).addCookie(cookie)
              }
            }.flatMapError { cause =>
              ZIO.logError(s"Failed to login user: ${cause.toString}") as Response.fromHttpError {
                HttpError.InternalServerError("Failed to login user.")
              }
            }
          }
    }

  // @@ csrfGenerate() // TODO: get this working?
  val logoutEndpoint = Http.collectZIO[Request] {
    case Method.POST -> !! / "logout" =>
      for {
        session <- RequestSession.get[UserSession]
        _       <- UserSessions.deleteUserSession(session.sessionId)
        _       <- ZIO.logInfo(s"Successfully logged out user ${session.userId} with cookie: ${session.sessionId.take(10)}")
      } yield Response.ok
    case Method.GET -> !! / "session" =>
      for {
        session <- RequestSession.get[UserSession]
      } yield Response.text(session.sessionId)
    case Method.GET -> !! / "token"   =>
      // Guaranteed to have a valid access token for next 60 min.
      for {
        session    <- RequestSession.get[UserSession]
        newSession <- UserSessions.refreshUserSession(session.sessionId)
      } yield Response.text(newSession.accessToken)
  }

  val generateRedirectUrl: URIO[SpotifyConfig, URL] = for {
    c     <- ZIO.service[SpotifyConfig]
    state <- Random.nextUUID
  } yield URL(
    Path.decode("/authorize"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443),
    QueryParams(
      "response_type" -> Chunk("code"),
      "client_id"     -> Chunk(c.clientID),
      "redirect_uri"  -> Chunk(c.redirectURI),
      "scope"         -> Chunk(scopes),
      "state"         -> Chunk(state.toString.take(15))
    )
  )

  /**
   * Handles a user login. TODO: add retry schedule.
   *
   * @param auth
   *   current user auth data from spotify
   * @return
   *   true if new User was created, false if current user was updated.
   */
  def handleUserLogin(auth: AuthCodeFlowData) =
    for {
      newSessionId <- Random.nextUUID.map(_.toString).map(SessionId(_))
      spotify      <- SpotifyService.live(auth.accessToken)
      userInfo     <- spotify.getCurrentUserProfile.retry(Schedule.recurs(2))
      spotifyUserId = userInfo.id
      _            <- ZIO.logInfo(s"Retrieved profile data for user $spotifyUserId")
      _            <- DatabaseService
                        .createOrUpdateUser(newSessionId, RefreshToken(auth.refreshToken), UserId(spotifyUserId))
                        .timeout(10.seconds)
                        .tapError(_ => ZIO.logError("Failed to process user login."))
      _            <- ZIO.logInfo(s"Successfully logged in $spotifyUserId.")
    } yield newSessionId
}
