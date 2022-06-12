package server

import zio.{Layer, Random, System, URIO, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.printLine
import zhttp.http.{Http, Method, Request, Response, Scheme, URL, *}
import zhttp.service.{Client, EventLoopGroup, ChannelFactory}
import zhttp.http.Middleware.bearerAuthZIO
import zio.Ref
import domain.tables.AppUser

object Protected {
  val USER_PATH   = "user"
  val bearerToken = "bearer_token"

  // Going to need Http contramap.
  val endpoints = Http.collectZIO[(AppUser, Request)] {
    case (appUser, request @ Method.GET -> !! / USER_PATH / "get") =>
      ZIO.succeed(Response.text("whoop"))
  }

  type SignedIn = Ref[Map[String, AppUser]]

  // TODO: check if expired?
  def getUserInfoFromToken(token: String): ZIO[SignedIn, String, AppUser] = for {
    usersRef <- ZIO.service[SignedIn]
    users    <- usersRef.get
    res      <- ZIO.fromOption(users.get(token)).orElseFail("Invalid Bearer Token")
  } yield res

  def isTokenValid(token: String): ZIO[SignedIn, String, Boolean] = for {
    usersRef <- ZIO.service[SignedIn]
    users    <- usersRef.get
  } yield users.contains(token)

  val authMiddleware = bearerAuthZIO(isTokenValid)
  // val userInfoMiddleware = Middleware.transformZIO.apply()
  // Middleware.transform.apply()
  // need basicAuth + transform middleware?
  // Want to only allow user data and have user data available in request handler.
  //

  // def verifyAuth()
}
