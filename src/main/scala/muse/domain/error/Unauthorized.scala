package muse.domain.error

import zhttp.http.{Http, HttpData, Response, Status}

final case class Unauthorized(msg: Option[String])
    extends Throwable(msg.map(m => s"Unauthorized: $m").getOrElse("Unauthorized")) {
  val http = Http.response(Response(Status.Unauthorized, data = HttpData.fromString(this.getMessage)))
}

object Unauthorized {
  def apply(msg: String): Unauthorized = Unauthorized(Some(msg))
}
