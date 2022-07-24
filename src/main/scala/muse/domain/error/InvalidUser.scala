package muse.domain.error

case class InvalidUser(id: String) extends Exception(s"Invalid User: $id")
