package muse.service.spotify

import muse.config.AppConfig
import muse.domain.spotify.{PlaylistTrack, Track, UserPlaylist}
import muse.utils.Givens.given
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.Console.printLine
import zio.{Schedule, Task, ZIO, ZIOAppDefault}

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date
import scala.annotation.tailrec
import scala.collection.immutable.AbstractSet
import scala.util.{Failure, Try}

// TODO: communal listening session, chat?
object ExampleSpotify extends ZIOAppDefault {
  val program = for {
    backend <- AsyncHttpClientZioBackend()
    clientCredentials <- SpotifyAuthService.getClientCredentials
    spotify = SpotifyAPI[Task](backend, clientCredentials.accessToken)
    w = WeeklyAnalysis(spotify)
    _ <- w.findDuplicates
  } yield ()

  override def run = program.provide(
    AppConfig.live,
    EventLoopGroup.auto(8) ++ ChannelFactory.auto
  )
}

trait Eq[T] {
  def hashCodes(t: T): Set[Int]
  extension (t: T) def getHashCodes: Set[Int] = hashCodes(t)
}

final class SetWithEq[T] private(items: Map[Int, T])(using e: Eq[T]) extends Set[T] {
  def iterator: Iterator[T] = items.values.iterator

  def contains(elem: T): Boolean = elem.getHashCodes.exists(items.contains)

  def getElem(elem: T): Option[T] = elem
    .getHashCodes
    .find(h => items.contains(h))
    .flatMap(items.get)

  def excl(elem: T): SetWithEq[T] = {
    val newElems = elem.getHashCodes.foldLeft(items)((acc, c) => acc - c)
    SetWithEq(newElems)
  }

  def incl(elem: T): SetWithEq[T] = {
    val newItems = elem.getHashCodes.foldLeft(items)((acc, c) => acc + (c -> elem))
    SetWithEq(newItems)
  }
}

object SetWithEq {
  def empty[T](using eq: Eq[T]) = SetWithEq(Map.empty)
}

case class WeeklyAnalysis(spotify: SpotifyAPI[Task]) {
  val formatter = DateTimeFormatter.ofPattern("M/d/yyyy")
  val TIMEZONE_ET = ZoneId.of("America/New_York")

  def formatInstant(i: Instant) = {
    formatter.format(LocalDate.ofInstant(i, TIMEZONE_ET))
  }

  given trackEq: Eq[PlaylistTrack] = (p: PlaylistTrack) => {
    val track = p.track
    val allArtistsCode = track.artists.map(_.id).toSet.hashCode()
    val firstArtistCode = track.artists.head.id.hashCode
    val songNameCode = track.name.split("-").head.trim.hashCode
    Set[Int](
      // track id.
      track.id.hashCode,
      // track name and all artist ids.
      track.name.hashCode + allArtistsCode,
      // Case where remix/remastered.
      // I Don't Owe You Anything - 2011 Remaster == I Don't Owe You Anything
      songNameCode + allArtistsCode,
      songNameCode + firstArtistCode,
      // track name and primary artist id.
      track.name.hashCode + firstArtistCode
    )
  }

  def findDuplicates: Task[Unit] = for {
    tracks <- findWeeklyTracks
    slipUps = findSlipUps(tracks)
    report = weeklyDuplicateReport(slipUps)
    _ <- printLine(report)
  } yield ()

  def weeklyDuplicateReport(results: Map[String, Vector[(PlaylistTrack, PlaylistTrack)]]) = {
    val numDuplicates = results.values.map(_.size).sum
    val report = results.toList.sortWith(_._2.size > _._2.size).map {
      case (name, errors) =>
        val errorRows = errors
          .map {
            case (atFault, original) =>
              val trackString =
                s"${atFault.track.name.padTo(60, ' ')} | ${atFault.track.artists.map(_.name).mkString(", ").padTo(40, ' ')}"
              val dateStringFault = formatInstant(atFault.addedAt)
              val dateStringOriginal = formatInstant(original.addedAt)
              val faultString =
                s"Original: ${original.addedBy.id} on $dateStringOriginal. Duplicate on $dateStringFault"
              s"${trackString.padTo(120, ' ')} || ${faultString.padTo(40, ' ')}"
          }
          .mkString("\n")
        s"""
           |*$name*, total errors: ${errors.size}
           |$errorRows
           |""".stripMargin
    }

    s"""
       |Total slip ups: $numDuplicates
       |
       |${report.mkString("\n\n")}
       |""".stripMargin
  }

  def findWeeklyTracks: Task[Vector[PlaylistTrack]] = for {
    weeklies <- getWeeklyPlaylists
    sortedOldToNew = weeklies.sortBy { case (_, d) => d.toEpochDay }.map(_._1)
    ids = sortedOldToNew.map(_.id)
    _ <- printLine(s"${weeklies.size} Playlists Found: ${sortedOldToNew.map(_.name).mkString(", ")}")
    tracks <- ZIO.foreachPar(ids)(spotify.getAllPlaylistTracks).map(_.flatten)
    _ <- printLine(s"${tracks.size} Tracks Found")
  } yield tracks

  def getWeeklyPlaylists: Task[Vector[(UserPlaylist, LocalDate)]] = for {
    playlists <-
      ZIO.foreachPar(Vector("hondosin", "tuckingfypo1", "jelortegui27"))(spotify.getAllUserPlaylists)
    allPlaylists = playlists.flatten
    _ <- printLine(s"Retrieved ${allPlaylists.size} playlists.")
  } yield allPlaylists
    .map(p => p -> Try(LocalDate.parse(p.name, formatter)))
    .withFilter { case (_, date) => date.isSuccess }
    .map { case (p, d) => p -> d.get }

  // (AtFault, Original)
  def findSlipUps(tracks: Seq[PlaylistTrack]): Map[String, Vector[(PlaylistTrack, PlaylistTrack)]] = {
    @tailrec
    def go(
            tracks: List[PlaylistTrack],
            acc: Map[String, Vector[(PlaylistTrack, PlaylistTrack)]],
            tracksSeen: SetWithEq[PlaylistTrack]): Map[String, Vector[(PlaylistTrack, PlaylistTrack)]] = {
      tracks match
        case Nil => acc
        case current :: next =>
          tracksSeen.getElem(current) match
            case None =>
              go(next, acc, tracksSeen.incl(current))
            case Some(original: PlaylistTrack) =>
              val user = current.addedBy.id
              val slipUps = acc.getOrElse(user, Vector.empty)
              val newAcc = acc + (user -> slipUps.appended(current -> original))
              go(next, newAcc, tracksSeen)
    }

    val sorted = tracks.sortWith(_.addedAt.toEpochMilli < _.addedAt.toEpochMilli)
    go(sorted.toList, Map.empty, SetWithEq.empty)
  }
}
