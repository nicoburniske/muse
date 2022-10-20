package muse.service.spotify

import muse.domain.spotify.ErrorResponse
import sttp.client3.DeserializationException as SttpDeserializationException
import sttp.model.{Method, ResponseMetadata, StatusCode, Uri}
import zio.json.JsonError

enum SpotifyError extends Throwable {
  case MalformedRequest(reason: String)
  // TODO: integrate optional http error?
  case TooManyRequests(uri: Uri, method: Method, body: Option[String])
  case HttpError(errorResponse: Either[List[String], ErrorResponse], uri: Uri, method: Method, code: StatusCode)
  case JsonError(error: String, received: String, uri: String)
  case DeserializationException[T](cause: SttpDeserializationException[T])

  override def getMessage: String = this match {
    case MalformedRequest(reason)                    => reason
    case TooManyRequests(uri, method, body)          =>
      s"Too many requests to $uri with method $method and body $body"
    case HttpError(errorResponse, uri, method, code) =>
      s"Error Code ${code} from ${method} $uri: $errorResponse"
    case JsonError(error, received, uri)             =>
      s"Json Error with Status  url $uri: $error\nJson received: $received"
    case DeserializationException(cause)             => cause.toString
  }
}
