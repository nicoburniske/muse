package muse.server.graphql

import muse.service.spotify.SpotifyService
import muse.domain.session.UserSession
import zio.*

object Helpers {
  def getSession = ZIO.serviceWithZIO[Reloadable[UserSession]](_.get)
  def getUserId  = getSession.map(_.userId)
  def getSpotify = ZIO.serviceWithZIO[Reloadable[SpotifyService]](_.get)
}
