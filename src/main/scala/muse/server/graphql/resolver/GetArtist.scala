package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Artist
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetArtist(id: String) extends Request[Throwable, Artist]

object GetArtist {
  val MAX_ARTISTS_PER_REQUEST = 20

  def query(artistId: String) = ZQuery.fromRequest(GetArtist(artistId))(ArtistDataSource)

  val ArtistDataSource: DataSource[RequestSession[SpotifyService], GetArtist] =
    DataSource.Batched.make("ArtistDataSource") { reqs =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_ARTISTS_PER_REQUEST,
          req => RequestSession.get[SpotifyService].flatMap(_.getArtist(req.id)),
          reqs => RequestSession.get[SpotifyService].flatMap(_.getArtists(reqs.map(_.id))),
          Artist.fromSpotify,
          _.id,
          _.id
        ).addTimeLog(s"Retrieved Artists ${reqs.size}")
    }

}
