package muse.service.spotify

import sttp.model.ResponseMetadata
import zio.json.JsonError

enum SpotifyError extends Throwable {
  case MalformedRequest(reason: String)
  case HttpError(message: String, metadata: ResponseMetadata, uri: String, params: String)
  case JsonError(error: String, received: String)
  override def getMessage: String = {
    this match {
      case MalformedRequest(reason: String)          => reason
      case HttpError(message, metadata, uri, params) =>
        s"Error Code ${metadata.code} from $uri $params: $message"
      case JsonError(error, received)                => s"Json Error: $error\nJson received: $received"
    }
  }
}
