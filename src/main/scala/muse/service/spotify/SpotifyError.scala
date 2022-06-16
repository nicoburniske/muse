package muse.service.spotify

import sttp.client3.HttpError
import sttp.model.ResponseMetadata
import zio.json.JsonError

enum SpotifyError extends Throwable {
  case MalformedRequest(reason: String)
  case HttpError(message: String, metadata: ResponseMetadata)
  case JsonError(error: String, received: String)
  override def getMessage: String = {
    this match {
      case MalformedRequest(reason: String) => reason
      case HttpError(message, metadata)     => s"Error Code ${metadata.code}: $message"
      case JsonError(error, received)       => s"Json Error: $error}, Json received: \n $received"
    }
  }
}
