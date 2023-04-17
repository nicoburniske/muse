package muse.service.persist

import io.getquill.{LowerCase, NamingStrategy, PostgresZioJdbcContext, SnakeCase}
import io.getquill.jdbczio.Quill
import muse.domain.common.EntityType
import muse.domain.table.AccessLevel
import muse.service.persist.QuillContext.{Decoder, Encoder, decoder, encoder}
import zio.{Schedule, durationInt}

import java.sql.Types

object QuillContext extends PostgresZioJdbcContext(NamingStrategy(SnakeCase, LowerCase)) {
  // Exponential backoff retry strategy for connecting to Postgres DB.
  val schedule        = Schedule.exponential(1.second) && Schedule.recurs(10)
  val dataSourceLayer = Quill.DataSource.fromPrefix("database").retry(schedule)

  given entityTypeDecoder: Decoder[EntityType] =
    decoder((index, row, session) => EntityType.fromOrdinal(row.getInt(index)))

  given entityTypeEncoder: Encoder[EntityType] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))

  given reviewAccessDecoder: Decoder[AccessLevel] =
    decoder((index, row, session) => AccessLevel.fromOrdinal(row.getInt(index)))

  given reviewAccessEncoder: Encoder[AccessLevel] =
    encoder(Types.INTEGER, (index, value, row) => row.setInt(index, value.ordinal))
}
