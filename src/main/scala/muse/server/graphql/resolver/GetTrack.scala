package muse.server.graphql.resolver

import muse.domain.common.EntityType
import muse.domain.error.InvalidEntity
import muse.server.graphql.subgraph.Track
import muse.service.RequestSession
import muse.service.spotify.SpotifyService
import muse.utils.Utils.addTimeLog
import zio.ZIO
import zio.query.{CompletedRequestMap, DataSource, Request, ZQuery}

case class GetTrack(id: String) extends Request[Throwable, Track]

object GetTrack {
  val MAX_TRACKS_PER_REQUEST = 50

  def query(trackId: String) = ZQuery.fromRequest(GetTrack(trackId))(TrackDataSource)

  val TrackDataSource: DataSource[RequestSession[SpotifyService], GetTrack] =
    DataSource.Batched.make("TrackDataSource") { reqs =>
      DatasourceUtils
        .createBatchedDataSource(
          reqs,
          MAX_TRACKS_PER_REQUEST,
          req =>
            RequestSession.get[SpotifyService].flatMap(_.getTrack(req.id)),
          reqs => RequestSession.get[SpotifyService].flatMap(_.getTracks(reqs.map(_.id))),
          Track.fromSpotify(_, None),
          _.id,
          _.id
        ).addTimeLog(s"Retrieved ${reqs.size}")
    }

}
