package persist

import zio.ZIOAppDefault
import zio._
import zio.Console._
import io.getquill._

object ExampleQueries extends ZIOAppDefault {

  override def run = {
    val layers  = ZEnv.live ++ (QuillContext.dataSourceLayer >+> DatabaseQueries.live)
    val program = for {
      user <- DatabaseQueries.getUserReviews("hondosin")
      _    <- printLine(s"User: ${user}")
      _    <- DatabaseQueries.createReview(NewReview("hondosin", false, 0, "abcd"))
      user <- DatabaseQueries.getUserReviews("hondosin")
      _    <- printLine(s"User: ${user}")
    } yield ()
    program.provide(layers)
  }

}
