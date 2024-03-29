package muse.domain.error

import muse.domain.common.EntityType
import zio.http.{Body, HttpError, Response}

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
  val code     = "FORBIDDEN"
  val message  = reason.fold("Forbidden")(s => s"Forbidden: $s")
  val response = Response.fromHttpError(HttpError.Forbidden(message))

final case class BadRequest(reason: Option[String]) extends MuseError:
  val code     = "INVALID_REQUEST"
  val message  = reason.fold("Invalid Request")(m => s"Invalid Request: $m")
  val response = Response.fromHttpError(HttpError.BadRequest(message))

object Forbidden:
  val empty                       = Forbidden(None)
  def apply(s: String): Forbidden = Forbidden(Some(s))
