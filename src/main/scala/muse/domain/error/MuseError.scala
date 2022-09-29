package muse.domain.error

import muse.domain.common.EntityType
import zhttp.http.{Http, HttpData, Response, Status}

sealed trait MuseError extends Throwable:
  def code: String
  def message: String
  override def getMessage = message

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

object Forbidden:
  val empty                       = Forbidden(None)
  def apply(s: String): Forbidden = Forbidden(Some(s))

object Unauthorized:
  val empty                            = Unauthorized(None)
  def apply(msg: String): Unauthorized = Unauthorized(Some(msg))
