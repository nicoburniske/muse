package muse.service.spotify

import muse.domain.spotify.ErrorResponse
import muse.service.spotify.SpotifyAPI.DeserializationException as SttpDeserializationException
import sttp.model.{Method, ResponseMetadata, StatusCode, Uri}
import zio.json.JsonError

enum SpotifyError extends Throwable {
  case MalformedRequest(reason: String)
  // TODO: integrate optional http error?
  case TooManyRequests(uri: Uri, method: Method, body: Option[String], metadata: ResponseMetadata)
  case HttpError(errorResponse: Either[List[String], ErrorResponse], uri: Uri, method: Method, metadata: ResponseMetadata)
  case JsonError(error: String, received: String, uri: String, method: Method, metadata: ResponseMetadata)
  case DeserializationException[T](uri: Uri, method: Method, cause: SttpDeserializationException[T])
  case RateLimited(retryAfter: Long)

  override def getMessage: String = this match {
    case MalformedRequest(reason)                          => reason
    case TooManyRequests(uri, method, body, metadata)      =>
      s"Too many requests to $uri with method $method and body $body"
    case HttpError(errorResponse, uri, method, metadata)   =>
      s"Error Code ${metadata.code} from ${method} $uri: $errorResponse"
    case JsonError(error, received, uri, method, metadata) =>
      s"Json Error with Status ${metadata.code}. method $method url $uri: $error\nJson received: $received"
    case DeserializationException(uri, method, cause)      =>
      s"Uri: $uri. Method: $method ${cause.toString}"
    case RateLimited(retryAfter)                           =>
      s"Rate limited. Blocked until ${retryAfter} epoch seconds."
  }
}
