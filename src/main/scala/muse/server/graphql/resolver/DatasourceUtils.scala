package muse.server.graphql.resolver

import zio.query.{CompletedRequestMap, Request}
import zio.{Chunk, ZIO}

object DatasourceUtils {
  def createBatchedDataSource[Env, Error, ResourceResult, Result, Req <: Request[Error, Result], K](
      requests: Chunk[Req],
      maxPerRequest: Int,
      singleReq: Req => ZIO[Env, Error, ResourceResult],
      multiReq: Vector[Req] => ZIO[Env, Error, Vector[ResourceResult]],
      transformResourceResult: ResourceResult => Result,
      requestKey: Req => K,
      responseKey: Result => K
  ): ZIO[Env, Nothing, CompletedRequestMap] =
    if (requests.length == 1) {
      val firstReq = requests.head
      singleReq(firstReq)
        .map(transformResourceResult)
        .either.map { result => CompletedRequestMap.empty.insert(firstReq)(result) }
    } else {
      ZIO
        .foreachPar(requests.grouped(maxPerRequest).toVector) { batch =>
          multiReq(batch.toVector).map(_.map(transformResourceResult)).either.map(batch -> _)
        }
        .map { (res: Vector[(Chunk[Req], Either[Error, Vector[Result]])]) =>
          res.foldLeft(CompletedRequestMap.empty) {
            case (map: CompletedRequestMap, (reqs, result)) =>
              result match
                case error @ Left(_) => reqs.foldLeft(map)((map, req) => map.insert(req)(error))
                case Right(results)  =>
                  val grouped = results.map(r => responseKey(r) -> r).toMap
                  reqs.foldLeft(map) { (map, req) =>
                    // Requests not found in response ARE IGNORED!
                    grouped.get(requestKey(req)).fold(map)(found => map.insert(req)(Right(found)))
                  }
          }
        }
    }
}
