package muse.domain.spotify

import zio.json.*

case class ErrorResponse(error: ErrorReason)
case class ErrorReason(status: Int, message: String)

object ErrorResponse {
  given decodeErrorResponse: JsonDecoder[ErrorResponse] = DeriveJsonDecoder.gen[ErrorResponse]
  given decodeErrorReason: JsonDecoder[ErrorReason]     = DeriveJsonDecoder.gen[ErrorReason]
}
