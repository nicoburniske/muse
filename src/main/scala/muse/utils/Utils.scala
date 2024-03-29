package muse.utils

import zio.*
import zio.ZIO.ifZIO
import zio.metrics.Metric.histogram
import zio.metrics.MetricKeyType.Histogram
import zio.metrics.{Metric, MetricKeyType, MetricLabel, MetricState}
import zio.nio.Buffer
import zio.nio.channels.*
import zio.nio.file.*
import zio.stream.{ZPipeline, ZStream}

import java.io.{File, IOException}
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.io.Source
import scala.util.Try

object Utils {

  def timer(
      name: String,
      chronoUnit: ChronoUnit
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] = {
    val boundaries = Histogram.Boundaries.exponential(1.0, 2.0, 100)
    val base       = histogram(name, boundaries).tagged(MetricLabel("time_unit", chronoUnit.toString.toLowerCase()))

    base.contramap[Duration] { (duration: Duration) =>
      val unit = chronoUnit match
        case ChronoUnit.MILLIS  => duration.toMillis
        case ChronoUnit.MINUTES => duration.toMinutes
        case ChronoUnit.HOURS   => duration.toHours
        case _                  => duration.get(chronoUnit)
      unit.toDouble
    }
  }

  extension [R, E, A](z: ZIO[R, E, A]) {
    def addTimeLog(message: String) = z.timed.flatMap {
      case (d, r) =>
        ZIO.logInfo(s"$message in ${d.toMillis}ms").as(r)
    }

    // TODO: Convert to currying?
    def addTimeLog(message: String, resultToString: A => String) = z.timed.flatMap {
      case (d, r) =>
        ZIO.logInfo(s"$message ${resultToString(r)} in ${d.toMillis}ms").as(r)
    }
  }

  /**
   * Creates the given file if it doesn't currently exist.
   *
   * @param path
   *   the file path
   * @return
   *   a program that creates a file
   */
  def createFileIfMissing(path: String): ZIO[Any, IOException, Unit] =
    createFileIfMissing(Path(path))

  def createFileIfMissing(path: Path) = Files.exists(path).flatMap {
    case true  => ZIO.unit
    case false => ZIO.logInfo("Creating File.") *> Files.createFile(path)
  }

  /**
   * Reads all the lines for the file at the provided path.
   *
   * @param path
   *   the file path
   * @return
   *   a stream of lines from the file
   */
  def readFile(path: String): ZStream[Any, Throwable, String] =
    ZStream
      .fromZIO(Files.exists(Path(path)))
      .flatMap {
        case true  =>
          ZStream
            .fromFileName(path)
            .via(ZPipeline.utf8Decode)
        case false => ZStream.empty
      }

  /**
   * Overwrites content to file at given path.
   *
   * @param path
   *   string path to file
   * @param content
   *   the content to write to the file
   * @return
   *   a program that writes the content to the given file
   */
  def writeToFile(path: String, content: String): ZIO[Scope, IOException, Unit] =
    createFileIfMissing(path) *> ZIO.scoped {
      AsynchronousFileChannel
        .open(
          Path(path),
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
        .flatMap { channel =>
          val chunk = Chunk(content.getBytes: _*)
          channel.writeChunk(chunk, 0L)
        }
    }

  /**
   * Writes the given stream of strings to a file. File content will be overwritten. The file will be created if it doesn't
   * already exist.
   * @param path
   *   file path
   * @param stream
   *   to be written
   * @tparam R
   *   the environment
   * @return
   *   a program that writes the stream to the given file
   */
  def writeToFile[R](path: String, stream: ZStream[Any, Nothing, String]): ZIO[R, IOException, Unit] =
    createFileIfMissing(path) *> ZIO.scoped(
      AsynchronousFileChannel
        .open(
          Path(path),
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
        .flatMap(fileChannel =>
          stream
            .map(_.getBytes)
            .map(Chunk(_: _*))
            .runFoldZIO(0L) { (offset, chunk) => fileChannel.writeChunk(chunk, offset).as(offset + chunk.length) })
        .unit
    )
}
