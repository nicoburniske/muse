package muse

import muse.config.RateLimitConfig
import muse.service.cache.{RedisService, RedisServiceLive}
import zio.*
import zio.redis.{CodecSupplier, Redis, RedisExecutor, SingleNodeExecutor}
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test.ZIOSpecDefault
import zio.test.Assertion.*
import zio.test.*
import zio.durationInt
import zio.redis.embedded.EmbeddedRedis
import zio.test.TestAspect.withLiveClock

object RedisServiceLiveSpec extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }

  final case class Item(id: String, price: Double)
  object Item {
    final def itemKey(item: Item): String   = itemKey(item.id)
    final def itemKey(item: String): String = s"item:$item"

    given Schema[Item] = DeriveSchema.gen[Item]
  }

  def spec = suite("Integration test") {
    test("cacheOrExecute") {
      for {
        random <- ZIO.random
        id     <- random.nextUUID.map(_.toString)
        price  <- random.nextDoubleBetween(0.0, 100.0)
        item    = Item(id, price)
        key     = Item.itemKey(item)

        service  <- ZIO.service[RedisService]
        redisRef <- ZIO.service[Reloadable[Redis]]
        redis    <- redisRef.get

        before  <- redis.get(key).returning[Item]
        execute <- service.cacheOrExecute(key, 10.seconds)(ZIO.succeed(item))

        _     <- ZIO.sleep(100.millis)
        after <- redis.get(key).returning[Item]
      } yield assert(before)(isNone) && assertTrue(after.get == item, execute == item)
    } +
      test("cacheOrExecuteBulk") {
        ZIO
          .foreach(Chunk.fill(10)(())) { _ =>
            for {
              random <- ZIO.random
              id     <- random.nextUUID.map(_.toString)
              price  <- random.nextDoubleBetween(0.0, 100.0)
              item    = Item(id, price)
            } yield item
          }.flatMap { items =>
            val (toStore, toRetrieve) = items.splitAt(5)

            val allIds   = items.map(_.id).toList
            val idToMap  = items.map(item => item.id -> item).toMap
            val getItems = (ids: List[String]) => ids.map(id => id -> idToMap(id)).toMap

            for {
              // Set up half of the items in the cache.
              redisRef <- ZIO.service[Reloadable[Redis]]
              redis    <- redisRef.get
              _        <- ZIO.foreachDiscard(toStore) { item =>
                            val key = Item.itemKey(item)
                            redis.set(key, item)
                          }

              service      <- ZIO.service[RedisService]
              ref          <- Ref.make(List.empty[String])
              result       <- service.cacheOrExecuteBulk(allIds, 10.seconds)(Item.itemKey) { ids =>
                                ref.set(ids) *> ZIO.succeed(getItems(ids))
                              }
              retrievedIds <- ref.get
            } yield assert(result)(hasSameElementsDistinct(items)) &&
              assert(retrievedIds)(hasSameElementsDistinct(toRetrieve.map(_.id)))
          }
      }
  }.provideShared(
    EmbeddedRedis.layer,
    SingleNodeExecutor.layer,
    Redis.layer.reloadableManual,
    RedisService.layer,
    ZLayer.succeed(ProtobufCodecSupplier),
    ZLayer.succeed(RateLimitConfig(10, 10.seconds))
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.withLiveClock
}
