package server

import zio.{Layer, Random, System, URIO, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Http, Method, Request, Response, Scheme, URL, *}
import zhttp.service.{Client, EventLoopGroup, ChannelFactory}
import zhttp.http.Middleware.{bearerAuthZIO, csrfValidate}
import zhttp.http.*
import zio.Ref
import domain.tables.AppUser

object Protected {
  val USER_PATH = "user"

  val endpoints = Http
    .collectZIO[RequestWithSession[AppUser]] {
      case RequestWithSession(appUser, request @ Method.GET -> !! / USER_PATH / "reviews") =>
        ZIO.succeed(Response.text("whoop"))
    }
    .contramapZIO[SignedIn, HttpError, (String, Request)] { case (token, req) => getSession(token, req) }
    .contramapZIO[SignedIn, HttpError, Request] { req =>
      req.bearerToken match {
        case Some(token) => ZIO.succeed(token -> req)
        case _           => ZIO.fail(HttpError.Unauthorized("Missing Bearer Token"))
      }
    }
    .catchAll(error => Http.response(error.toResponse))

  // Introduce this after auth session middleware
  // @@ csrfValidate()


  type SignedIn = Ref[Map[String, AppUser]]
  case class RequestWithSession[A](session: A, request: Request)

  def getSession(token: String, req: Request): ZIO[SignedIn, HttpError, RequestWithSession[AppUser]] =
    getUserSessionFromToken(token).mapBoth(HttpError.Unauthorized(_), RequestWithSession(_, req))

  // TODO: check for expiration
  def getUserSessionFromToken(token: String): ZIO[SignedIn, String, AppUser] = for {
    usersRef <- ZIO.service[SignedIn]
    users    <- usersRef.get
    res      <- ZIO.fromOption(users.get(token)).orElseFail("Invalid Bearer Token")
  } yield res

}
