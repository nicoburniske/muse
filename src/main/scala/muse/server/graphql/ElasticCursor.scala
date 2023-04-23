package muse.server.graphql

import caliban.relay.Cursor
import caliban.schema.Schema

import java.util.Base64
import scala.util.Try

final case class ElasticCursor(value: String)

object ElasticCursor {
  lazy val decoder = Base64.getDecoder
  lazy val encoder = Base64.getEncoder

  given schema: Schema[Any, ElasticCursor] = Schema.stringSchema.contramap(
    Cursor[ElasticCursor].encode
  )

  given Cursor[ElasticCursor] = new Cursor[ElasticCursor] {
    type T = String
    def encode(a: ElasticCursor): String                 = {
      encoder.encodeToString(s"cursor:${a.value}".getBytes("UTF-8"))
    }
    def decode(s: String): Either[String, ElasticCursor] =
      Try(
        ElasticCursor(
          new String(decoder.decode(s), "UTF-8").replaceFirst("cursor:", "")
        )
      ).toEither.left.map(t => t.toString)

    def value(c: ElasticCursor): T = c.value
  }
}

