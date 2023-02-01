package muse.server

import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.session.UserSession
import muse.domain.spotify.AuthCodeFlowData
import muse.domain.table.User
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import sttp.client3.SttpBackend
import zhttp.http.*
import zhttp.http.Middleware.csrfGenerate
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Cause, Layer, Random, Ref, Schedule, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer, durationInt}

object Auth {
  val scopes = List(
    "user-library-read",
    "playlist-read-private",
    "playlist-read-collaborative",
    "user-modify-playback-state",
    "user-read-playback-state",
    "user-read-currently-playing",
    "user-library-modify",
    // Streaming permissions!
    "user-read-email",
    "user-read-private",
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
            ZIO.succeed(Response(status = Status.BadRequest, data = HttpData.fromString("Missing 'code' query parameter")))
          } { code =>
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

  lazy val logEndpoint = Middleware
    .identity[Request, Response].contramapZIO[Request](request => {
      ZIO.logInfo(s"Request: ${request.method} ${request.url}").as(request)
    })

  val generateRedirectUrl: URIO[SpotifyConfig, URL] = for {
    c     <- ZIO.service[SpotifyConfig]
    state <- Random.nextUUID
  } yield URL(
    Path.decode("/authorize"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443),
    Map(
      "response_type" -> List("code"),
      "client_id"     -> List(c.clientID),
      "redirect_uri"  -> List(c.redirectURI),
      "scope"         -> List(scopes),
      "state"         -> List(state.toString.take(15))
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
      uuid         <- Random.nextUUID
      newSessionId  = uuid.toString
      spotify      <- SpotifyService.live(auth.accessToken)
      userInfo     <- spotify.getCurrentUserProfile.retry(Schedule.recurs(2))
      spotifyUserId = userInfo.id
      _            <- ZIO.logInfo(s"Retrieved profile data for user $spotifyUserId")
      _            <- DatabaseService
                        .createOrUpdateUser(newSessionId, auth.refreshToken, spotifyUserId)
                        .timeout(10.seconds)
                        .tapError(_ => ZIO.logError("Failed to process user login."))
      _            <- ZIO.logInfo(s"Successfully logged in $spotifyUserId.")
    } yield newSessionId
}
