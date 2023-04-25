package muse.service.persist

import org.flywaydb.core.Flyway
import zio.{Cause, Task, ZIO, ZLayer}

import javax.sql.DataSource

trait MigrationService {
  def runMigrations: Task[Unit]
}

object MigrationService {
  val layer = ZLayer.fromFunction(MigrationServiceLive.apply)

  def runMigrations = ZIO.serviceWithZIO[MigrationService](_.runMigrations)
}

case class MigrationServiceLive(datasource: DataSource) extends MigrationService {
  override def runMigrations = for {
    _      <- ZIO.logInfo("Starting Flyway Migrations")
    flyway <- ZIO
                .attempt {
                  Flyway
                    .configure()
                    .dataSource(datasource)
                    .load()
                }
                .tapErrorCause(e => ZIO.logErrorCause(s"Error while loading flyway: $e", e))
    _      <- ZIO
                .attempt(flyway.migrate())
                .tapErrorCause(e => ZIO.logErrorCause(s"Error while running flyway migrations: $e", e))
    _      <- ZIO.logInfo("Flyway migrations ran successfully")
  } yield ()
}
