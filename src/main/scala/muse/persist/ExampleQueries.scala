package muse.persist

import muse.domain.common.EntityType
import muse.domain.create.CreateReview
import zio.ZIOAppDefault
import zio.*
import zio.Console.*
import io.getquill.*
import muse.utils.Parallel

object ExampleQueries extends ZIOAppDefault {
  override def run = {
    val layers  = ZEnv.live ++ (QuillContext.dataSourceLayer >+> DatabaseQueries.live)
    val program = for {
      user <- DatabaseQueries.getUserReviews("tuckingfypo1")
      _    <- printLine(s"User: $user")
      _    <- DatabaseQueries.createReview(
                "tuckingfypo1",
                CreateReview(
                  "are alden's bumps all that hard?",
                  true,
                  EntityType.Playlist,
                  "5AGvFHPvaNS8SgVb9xAax3"))
      _    <- DatabaseQueries.createReview(
                "tuckingfypo1",
                CreateReview("Is RadioHead Overrated?", true, EntityType.Album, "1HrMmB5useeZ0F5lHrMvl0"))
      _    <- DatabaseQueries.createReview(
                "tuckingfypo1",
                CreateReview("RICEWINE might be a genius", true, EntityType.Track, "0gdWSthwNMJ4TPVya8b0bh"))
      _    <- DatabaseQueries.createReview(
                "tuckingfypo1",
                CreateReview("The next big thing", true, EntityType.Artist, "68YGyJRfQQG9HqiMpgoRiV"))
      user <- DatabaseQueries.getUserReviews("tuckingfypo1")
      _    <- printLine(s"User: $user")
    } yield ()
    program.provide(layers)
  }

}
