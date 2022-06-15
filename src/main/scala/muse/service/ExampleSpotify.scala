package muse.service

import zio.*
import zio.Task
import zio.Console.*
import sttp.client3.*
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.monad.MonadError
import muse.utils.Givens.given

object ExampleSpotify extends ZIOAppDefault {
  val accessToken  =
    "BQCynGsdSSxsrHDgGLMmFE5Xof7a82ibLaXFHr15ekC-dLJOUu4zFZQrUtRHwAtZNOSl2a_VC2nghPuMFBijsAKfol0f3Geq8Yzar3q8jTQWEF-EzHnwsvQFFXH9e4e6H2zPlf6gZeMoPR3_IXdNXCsTIEU4D8aHWTesUbrRYDa9k8E4M7_CYaR0Q82K-w"
  val refreshToken = ""

  override def run = {
    val program = for {
      backend  <- AsyncHttpClientZioBackend()
      spotify   = SpotifyService[Task](backend, accessToken, refreshToken)
      response <- spotify.getAllPlaylistTracks("4FXSFL5xzbK9iSS1vpp2zd")
      _        <- printLine(response)
    } yield ()
    program
  }

}
