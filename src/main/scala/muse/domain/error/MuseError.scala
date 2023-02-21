package muse.domain.error

import muse.domain.common.EntityType
import zhttp.http.{Http, HttpData, Response, Status}

sealed trait MuseError extends Throwable:
  override def getMessage = message

  def code: String
  def message: String

final case class InvalidEntity(entityId: String, entityType: EntityType) extends MuseError:
  val code    = "INVALID_ENTITY"
  val message = s"Invalid $entityType: $entityId"

final case class InvalidUser(id: String) extends MuseError:
  val code    = "INVALID_USER"
  val message = s"Invalid User: $id"

final case class Forbidden(reason: Option[String]) extends MuseError:
  val code    = "FORBIDDEN"
  val message = reason.fold("Forbidden")(s => s"Forbidden: $s")
  val http    = Http.response(Response(Status.Forbidden, data = HttpData.fromString(message)))

final case class Unauthorized(reason: Option[String]) extends MuseError:
  val code    = "UNAUTHORIZED"
  val message = reason.fold("Unauthorized")(m => s"Unauthorized: $m")
  val http    = Http.response(Response(Status.Unauthorized, data = HttpData.fromString(message)))

final case class BadRequest(reason: Option[String]) extends MuseError:
  val code    = "INVALID_REQUEST"
  val message = reason.fold("Invalid Request")(m => s"Invalid Request: $m")

object RateLimited extends MuseError:
  val code    = "RATE_LIMITED"
  val message = "Too many concurrent requests"
  val http    = Http.response(Response(Status.TooManyRequests, data = HttpData.fromString(message)))

object Forbidden:
  val empty                       = Forbidden(None)
  def apply(s: String): Forbidden = Forbidden(Some(s))

object Unauthorized:
  val empty                            = Unauthorized(None)
  def apply(msg: String): Unauthorized = Unauthorized(Some(msg))
