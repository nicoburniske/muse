package muse.utils

import zio.ZIO
import zio._
import zio.nio.channels._
import zio.nio.file._

import java.io.IOException
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.temporal.ChronoUnit

object Utils {
  val DEFAULT_EXPIRATION_PADDING = 30

  /**
   * @param expiresIn
   * Number of seconds from now.
   * @param padding
   * Amount of padding expiration should be created with. Must be greater than expiresIn
   * @return
   * The instant when something has expired
   */
  def getExpirationInstant(expiresIn: Int, padding: Int = DEFAULT_EXPIRATION_PADDING) =
    ZIO.succeed(Instant.now().plus(expiresIn - padding, ChronoUnit.SECONDS))

  extension[R, E, A] (z: ZIO[R, E, A]) {
    def addTimeLog(message: String) = z.timed.flatMap {
      case (d, r) =>
        ZIO.logInfo(s"$message in ${d.toMillis}ms").as(r)
    }

    def addTimeLog(message: String, resultToString: A => String) = z.timed.flatMap {
      case (d, r) =>
        ZIO.logInfo(s"$message ${resultToString(r)} in ${d.toMillis}ms").as(r)
    }
  }

  /**
   * Writes content to file at given path.
   *
   * @param path
   * string path to file
   * @param content
   * the content to write to the file
   * @return
   * a program that writes the content to the given file
   */
  def writeToFile(path: String, content: String): ZIO[Scope, IOException, Unit] = ZIO.scoped {
    AsynchronousFileChannel
      .open(
        Path(path),
        StandardOpenOption.WRITE
      )
      .flatMap { channel =>
        val chunk = Chunk(content.getBytes: _*)
        channel.writeChunk(chunk, 0L)
      }
  }
}
