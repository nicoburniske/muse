package muse.domain.spotify

import zio.json.*

enum ReleaseDatePrecision:
  case Day
  case Month
  case Year

object ReleaseDatePrecision {
  given JsonDecoder[ReleaseDatePrecision] = JsonDecoder[String].map {
    case "day"   => ReleaseDatePrecision.Day
    case "month" => ReleaseDatePrecision.Month
    case "year"  => ReleaseDatePrecision.Year
  }
}
