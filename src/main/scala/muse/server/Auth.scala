package muse.server

import muse.config.{ServerConfig, SpotifyConfig}
import muse.domain.session.UserSession
import muse.domain.spotify.AuthCodeFlowData
import muse.domain.table.AppUser
import muse.service.persist.DatabaseService
import muse.service.spotify.{SpotifyAuthService, SpotifyService}
import muse.service.{RequestSession, UserSessions}
import sttp.client3.SttpBackend
import zhttp.http.*
import zhttp.http.Middleware.csrfGenerate
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Cause, Layer, Random, Ref, Schedule, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer}

object Auth {
  val scopes = List(
    "user-library-read",
    "playlist-read-private",
    "playlist-read-collaborative",
    "user-modify-playback-state",
    "user-read-playback-state",
    "user-read-currently-playing"
  ).mkString(" ")

  val loginEndpoints = Http
    .collectZIO[Request] {
      case Method.GET -> !! / "login"          =>
        generateRedirectUrl.map(url => Response.redirect(url.encode, false))
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
              authData    <- SpotifyAuthService.getAuthCode(code)
              spotifyUser <- handleUserLogin(authData)
              frontendURL <- ZIO.serviceWith[ServerConfig](_.frontendUrl)
              session     <- UserSessions.addUserSession(spotifyUser.id, authData)
              _           <- ZIO.logInfo(session)
            } yield {
              // TODO: add domain to cookie.
              val cookie = Cookie(
                COOKIE_KEY,
                session,
                isSecure = true,
                // NOTE: This is so that websockets can be used.
                isHttpOnly = false)
              Response.redirect(frontendURL).addCookie(cookie)
            }
          }
    }

  // @@ csrfGenerate() // TODO: get this working?
  val logoutEndpoint = Http
    .collectZIO[Request] {
      case Method.POST -> !! / "logout" =>
        for {
          session <- RequestSession.get[UserSession]
          _       <- UserSessions.deleteUserSession(session.sessionCookie)
          _       <- ZIO.logInfo(s"Successfully logged out user ${session.id} with cookie: ${session.sessionCookie.take(10)}")
        } yield Response.ok
    }

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
      spotify  <- SpotifyService.live(auth.accessToken)
      userInfo <- spotify.getCurrentUserProfile.retry(Schedule.recurs(2))
      id        = userInfo.id
      res      <- DatabaseService.createOrUpdateUser(id)
      resText   = if (res) "Created" else "Updated"
      _        <- ZIO.logInfo(s"Successfully logged in $id. $resText account")
    } yield userInfo
}
