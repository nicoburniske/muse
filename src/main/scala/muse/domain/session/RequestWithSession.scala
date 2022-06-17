package muse.domain.session

import zhttp.http.Request

final case class RequestWithSession[A](session: A, request: Request)
