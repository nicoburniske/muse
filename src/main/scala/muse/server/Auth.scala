package muse.server

import muse.config.SpotifyConfig
import muse.domain.session.UserSession
import muse.domain.spotify.InitialAuthData
import muse.domain.table.AppUser
import muse.server.MuseMiddleware.Auth
import muse.service.spotify.SpotifyAuthServiceLive
import muse.service.{RequestProcessor, UserSessions}
import sttp.client3.SttpBackend
import zhttp.http.*
import zhttp.http.Middleware.csrfGenerate
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zio.{Layer, Random, Ref, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer}

object Auth {
  val scopes = List("user-read-recently-played", "user-follow-read", "ugc-image-upload").mkString(" ")

  type AuthEnv = SpotifyConfig & EventLoopGroup & ChannelFactory & UserSessions

  val endpoints = Http
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
            ZIO.succeed(
              Response(
                status = Status.BadRequest,
                data = HttpData.fromString("Missing 'code' query parameter")))
          } { code =>
            for {
              authData    <- SpotifyAuthServiceLive.getAuthTokens(code)
              spotifyUser <- RequestProcessor.handleUserLogin(authData)
              session     <- UserSessions.addUserSession(spotifyUser.id, authData)
              _           <- ZIO.logInfo(session)
            } yield {
              // TODO: yield redirect to actual site
              // TODO: add domain to cookie.
              val cookie = Cookie(COOKIE_KEY, session, isSecure = true, isHttpOnly = true)
              Response.redirect("http://localhost:3000").addCookie(cookie)
            }
          }
    }
    .catchAll(error => Http.error(HttpError.InternalServerError(cause = Some(error))))
  // @@ csrfGenerate() // TODO: get this working?

  val generateRedirectUrl: URIO[SpotifyConfig, URL] = for {
    c     <- ZIO.service[SpotifyConfig]
    state <- Random.nextUUID
  } yield URL(
    Path.decode("authorize"),
    URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443),
    Map(
      "response_type" -> List("code"),
      "client_id"     -> List(c.clientID),
      "redirect_uri"  -> List(c.redirectURI),
      "scope"         -> List(scopes),
      "state"         -> List(state.toString.take(15))
    )
  )
}
