package muse.service.spotify

import muse.utils.Givens.given
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.Console.printLine
import zio.{Task, ZIOAppDefault}

object ExampleSpotify extends ZIOAppDefault {
  val accessToken  =
    "BQCynGsdSSxsrHDgGLMmFE5Xof7a82ibLaXFHr15ekC-dLJOUu4zFZQrUtRHwAtZNOSl2a_VC2nghPuMFBijsAKfol0f3Geq8Yzar3q8jTQWEF-EzHnwsvQFFXH9e4e6H2zPlf6gZeMoPR3_IXdNXCsTIEU4D8aHWTesUbrRYDa9k8E4M7_CYaR0Q82K-w"
  val refreshToken = ""

  override def run = {
    val program = for {
      backend  <- AsyncHttpClientZioBackend()
      spotify   = SpotifyAPI[Task](backend, accessToken)
      response <- spotify.getAllPlaylistTracks("4FXSFL5xzbK9iSS1vpp2zd")
      _        <- printLine(response)
    } yield ()
    program
  }

}
