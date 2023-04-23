package muse.service.persist

import io.getquill.jdbczio.Quill
import io.getquill.*
import muse.domain.common.EntityType
import muse.domain.common.Types.{RefreshToken, SessionId, UserId}
import muse.domain.table.AccessLevel
import muse.service.persist.QuillContext.{Decoder, Encoder, decoder, encoder}
import zio.{Schedule, durationInt}

import java.time.Instant
import java.sql.Types

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  // Exponential backoff retry strategy for connecting to Postgres DB.
  val schedule        = Schedule.exponential(1.second) && Schedule.recurs(10)
  val dataSourceLayer = Quill.DataSource.fromPrefix("database").retry(schedule)

  given Encoder[EntityType] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

  given Decoder[EntityType] =
    decoder((index, row, _) => EntityType.fromOrdinal(row.getInt(index)))

  given Encoder[AccessLevel] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

  given Decoder[AccessLevel] =
    decoder((index, row, _) => AccessLevel.fromOrdinal(row.getInt(index)))

  given Encoder[UserId] =
    encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value))

  given Decoder[UserId] =
    decoder((index, row, _) => UserId(row.getString(index)))

  given Encoder[SessionId] =
    encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value))

  given Decoder[SessionId] = decoder((index, row, _) => SessionId(row.getString(index)))

  given Encoder[RefreshToken] =
    encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value))

  given Decoder[RefreshToken] =
    decoder((index, row, _) => RefreshToken(row.getString(index)))

  extension (inline a: Instant)
    inline def >(b: Instant) = quote { sql"($a > $b)".as[Boolean] }
    inline def <(b: Instant) = quote { sql"($a < $b)".as[Boolean] }
}
