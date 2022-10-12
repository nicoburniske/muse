package muse.service.persist

import org.flywaydb.core.Flyway
import zio.{Cause, Task, ZIO, ZLayer}

import javax.sql.DataSource

trait MigrationService {
  def clean: Task[Unit]
  def runMigrations: Task[Unit]
//  def runBaseline: Task[Unit]
//  def repairMigrations: Task[Unit]
}

object MigrationService {
  val layer = ZLayer.fromFunction(MigrationServiceLive.apply)

  def runMigrations = ZIO.serviceWithZIO[MigrationService](_.runMigrations)
}

case class MigrationServiceLive(datasource: DataSource) extends MigrationService {
  override def clean         = ???
  override def runMigrations = for {
    flyway <- ZIO
                .attempt {
                  Flyway
                    .configure()
                    .dataSource(datasource)
                    .load()
                }
                .tapError(e => ZIO.logErrorCause(s"Error while loading flyway: $e", Cause.fail(e)))
    _      <- ZIO
                .attempt(flyway.migrate())
                .tapError(e => ZIO.logErrorCause(s"Error while running flyway migrations: $e", Cause.fail(e)))
    _      <- ZIO.logInfo("Flyway migrations ran successfully")
  } yield ()
}
