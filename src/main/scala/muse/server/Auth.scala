package muse.server

import zio.{Layer, Random, Ref, System, Task, URIO, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Http, Method, Request, Response, Scheme, URL, *}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.json.*
import zhttp.http.Middleware.csrfGenerate
import muse.domain.spotify.AuthData
import muse.config.SpotifyConfig
import muse.domain.tables.AppUser
import muse.service.RequestProcessor
import muse.service.RequestProcessor.UserLoginEnv
import sttp.client3.SttpBackend

object Auth {
  val scopes = List("user-read-recently-played").mkString(" ")

  type AuthEnv = SpotifyConfig & EventLoopGroup & ChannelFactory

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
              authData <- getAuthTokens(code)
              appUser  <- RequestProcessor.handleUserLogin(authData)
              session  <- Random.nextUUID
              usersRef <- ZIO.service[Ref[Map[String, AppUser]]]
              _        <- usersRef.update(_ + (session.toString -> appUser))
              users    <- usersRef.get
              _        <- printLine("Users:\n" + users.mkString("\n"))
            } yield {
              // TODO: yield redirect to actual site
              // TODO: should make httponly? Cookie is not updating in browse?
              val cookie = Cookie("xsession", session.toString, domain = Some("muse.io"))
              Response.text("You're logged in fool!").addCookie(cookie)
            }
        }
    }
    .catchAll(error => Http.error(HttpError.InternalServerError(cause = Some(error))))
  // @@ csrfGenerate() // TODO: get this working?
  // TODO: Error handling for routes!

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

  def getAuthTokens(code: String): ZIO[AuthEnv, Throwable, AuthData] =
    for {
      response <- requestAccessToken(code)
      body     <- response.bodyAsString
      token    <- body.fromJson[AuthData] match {
                    case Left(error) => ZIO.fail(new Exception(error))
                    case Right(data) => ZIO.succeed(data)
                  }
    } yield token

  def requestAccessToken(code: String): ZIO[AuthEnv, Throwable, Response] =
    ZIO.service[SpotifyConfig].flatMap { c =>
      val url     = URL(
        Path.decode("api/token"),
        URL.Location.Absolute(Scheme.HTTPS, "accounts.spotify.com", 443)
      )
      val body    = Map(
        "grant_type"   -> "authorization_code",
        "code"         -> code,
        "redirect_uri" -> c.redirectURI
      )
      val headers = Headers.basicAuthorizationHeader(c.clientID, c.clientSecret) ++
        Headers.contentType(HeaderValues.applicationXWWWFormUrlencoded)
      Client.request(url.encode, Method.POST, headers, HttpData.fromString(encodeFormBody(body)))
    }

  // TODO: add to ZIO-HTTP
  def encodeFormBody(data: Map[String, String]): String =
    data.map { case (k, v) => s"$k=$v" }.mkString("&")

}
