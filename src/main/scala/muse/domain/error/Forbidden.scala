package muse.domain.error

final case class Forbidden(reason: Option[String])
    extends Throwable(reason.fold("Forbidden")(s => s"Forbidden: $s"))

object Forbidden {
  def apply(s: String): Forbidden = Forbidden(Some(s))
}
