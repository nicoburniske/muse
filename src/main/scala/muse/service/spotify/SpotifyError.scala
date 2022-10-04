package muse.service.spotify

import muse.domain.spotify.ErrorResponse
//import sttp.client3.HttpError as SttpHttpError
import sttp.client3.DeserializationException as SttpDeserializationException
import sttp.model.{Method, ResponseMetadata, Uri}
import zio.json.JsonError

enum SpotifyError extends Throwable {
  case MalformedRequest(reason: String)
  // TODO: integrate optional http error?
  case HttpError(errorResponse: ErrorResponse, uri: Uri, method: Method)
  case JsonError(error: String, received: String, uri: String)
  case DeserializationException[T](cause: SttpDeserializationException[T])

  override def getMessage: String = this match {
    case MalformedRequest(reason)              => reason
    case HttpError(errorResponse, uri, method) =>
      s"Error Code ${errorResponse.error.status} from ${method.method} $uri: ${errorResponse.error.message}"
    case JsonError(error, received, uri)       =>
      s"Json Error for url ${uri}: $error\nJson received: $received"
    case DeserializationException(cause)       => cause.toString
  }
}
