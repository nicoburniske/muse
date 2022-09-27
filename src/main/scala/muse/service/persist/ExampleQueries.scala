package muse.service.persist

import io.getquill.*
import muse.domain.common.EntityType
import muse.domain.mutate.CreateReview
import zio.*
import zio.Console.*

object ExampleQueries extends ZIOAppDefault {
  override def run = {
    val layers  = QuillContext.dataSourceLayer >+> DatabaseService.layer
    val program = for {
      user <- DatabaseService.getUserReviews("tuckingfypo1")
      _    <- printLine(s"User: $user")
      _    <- DatabaseService.createReview(
                "notarealuser",
                CreateReview("I'm not real", true, EntityType.Artist, "3FjdJbt6Myq32uv7P4owM1"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview(
                  "Adrian's Impeccable taste in music",
                  true,
                  EntityType.Playlist,
                  "4sZt86qDfcYJl7gTttPjIL"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview(
                  "are alden's bumps all that hard?",
                  true,
                  EntityType.Playlist,
                  "5AGvFHPvaNS8SgVb9xAax3"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview("Is RadioHead Overrated?", true, EntityType.Album, "1HrMmB5useeZ0F5lHrMvl0"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview("RICEWINE might be a genius", true, EntityType.Track, "0gdWSthwNMJ4TPVya8b0bh"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview("The next big thing", true, EntityType.Artist, "68YGyJRfQQG9HqiMpgoRiV"))
      _    <- DatabaseService.createReview(
                "tuckingfypo1",
                CreateReview("Argentine Rock Icon", true, EntityType.Track, "6XoyfaS2X22S3IwzYJwPzd")
              )
      user <- DatabaseService.getUserReviews("tuckingfypo1")
      _    <- printLine(s"User: $user")
    } yield ()
    program.provide(layers)
  }

}
