package muse.server

import zio.{Layer, Random, Ref, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.*
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zhttp.http.Middleware.csrfGenerate
import muse.domain.spotify.InitialAuthData
import muse.config.SpotifyConfig
import muse.domain.tables.AppUser
import muse.service.spotify.SpotifyAuthServiceLive
import muse.service.{RequestProcessor, UserSessions}
import muse.service.RequestProcessor.UserLoginEnv
import sttp.client3.SttpBackend

object Auth {
  val scopes = List("user-read-recently-played", "user-follow-read", "ugc-image-upload").mkString(" ")

  type AuthEnv = SpotifyConfig & EventLoopGroup & ChannelFactory & UserSessions

  val endpoints = Http
    .collectZIO[Request] {
      case Method.GET -> !! / "login"          =>
        for {
          url <- generateRedirectUrl()
        } yield Response.redirect(url.encode, false)
      case req @ Method.GET -> !! / "callback" =>
        req.url.queryParams.get("code").flatMap(_.headOption) match {
          case None       =>
            ZIO.succeed(
              Response(
                status = Status.BadRequest,
                data = HttpData.fromString("Missing 'code' query parameter")))
          case Some(code) =>
            for {
              authData    <- SpotifyAuthServiceLive.getAuthTokens(code)
              spotifyUser <- RequestProcessor.handleUserLogin(authData)
              session     <- UserSessions.addUserSession(spotifyUser.id, authData)
              _           <- printLine(session)
            } yield {
              // TODO: yield redirect to actual site
              // TODO: should make httponly?
              // TODO: add domain to cookie.
              val cookie = Cookie("xsession", session)
              Response.text("You're logged in fool!").addCookie(cookie)
            }
        }
    }
    .catchAll(error => Http.error(HttpError.InternalServerError(cause = Some(error))))
  // @@ csrfGenerate() // TODO: get this working?

  def generateRedirectUrl(): URIO[SpotifyConfig, URL] = for {
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
