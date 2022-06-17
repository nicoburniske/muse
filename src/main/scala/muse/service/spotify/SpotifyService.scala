package muse.service.spotify

import muse.config.SpotifyConfig
import muse.domain.session.UserSession
import muse.domain.spotify.*
import muse.service.UserSessions
import muse.utils.Givens.given
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.SttpClient
import sttp.model.StatusCode
import zio.{Schedule, Task, ZIO}

object SpotifyService {
  val live = for {
    user    <- ZIO.service[UserSession]
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, user.accessToken)

  def live(accessToken: String) = for {
    backend <- ZIO.service[SttpBackend[Task, Any]]
  } yield SpotifyAPI(backend, accessToken)
}
