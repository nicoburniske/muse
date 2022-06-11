package persist

import domain.create.CreateReview
import zio.ZIOAppDefault
import zio.*
import zio.Console.*
import io.getquill.*

object ExampleQueries extends ZIOAppDefault {
  override def run = {
    val layers  = ZEnv.live ++ (QuillContext.dataSourceLayer >+> DatabaseQueries.live)
    val program = for {
      user <- DatabaseQueries.getUserReviews("hondosin")
      _    <- printLine(s"User: ${user}")
      _    <- DatabaseQueries.createReview(CreateReview("hondosin", false, 0, "abcd"))
      user <- DatabaseQueries.getUserReviews("hondosin")
      _    <- printLine(s"User: ${user}")
    } yield ()
    program.provide(layers)
  }

}
