package muse

import muse.service.RedisService
import zio.{ZIO, ZLayer}
import zio.redis.{CodecSupplier, Redis, RedisExecutor, SingleNodeExecutor}
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test.ZIOSpecDefault
import zio.test.Assertion.*
import zio.test.*
import zio.durationInt
import zio.redis.embedded.EmbeddedRedis
import zio.test.TestAspect.withLiveClock

object RedisServiceSpec extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }

  final case class Item(id: String, price: Double)
  object Item {
    final def itemKey(item: Item): String = s"item:${item.id}"

    given Schema[Item] = DeriveSchema.gen[Item]
  }

  def spec = suite("Integration test") {
    test("get and set strings") {
      for {
        key    <- ZIO.random.flatMap(_.nextUUID).map(_.toString)
        value  <- ZIO.random.flatMap(_.nextUUID).map(_.toString)
        _      <- ZIO.logInfo(s"String key $key with value $value")
        redis  <- ZIO.service[Redis]
        _      <- ZIO.logInfo("Setting string key")
        _      <- redis.set(key, value)
        _      <- ZIO.logInfo("Getting string value")
        result <- redis.get(key).returning[String]
      } yield assert(result)(isSome(equalTo(value)))
    } + test("get and set positive integers") {
      for {
        key    <- ZIO.random.flatMap(_.nextIntBetween(0, Int.MaxValue))
        value  <- ZIO.random.flatMap(_.nextIntBetween(0, Int.MaxValue))
        _      <- ZIO.logInfo(s"Int key $key with value $value")
        redis  <- ZIO.service[Redis]
        _      <- ZIO.logInfo("Setting Int key")
        _      <- redis.set(key, value)
        _      <- ZIO.logInfo("Getting Int value")
        result <- redis.get(key).returning[Int]
      } yield assert(result)(isSome(equalTo(value)))
    } +
      test("get and set items") {
        val item = Item("1", 1.0)
        val key  = Item.itemKey(item)
        for {
          redis  <- ZIO.service[Redis]
          _      <- ZIO.logInfo("Setting item")
          _      <- redis.set(key, item)
          _      <- ZIO.logInfo("Getting item")
          result <- redis.get(key).returning[Item]
        } yield assert(result)(isSome(equalTo(item)))
      } +
      test("Redis Service get and set items") {
        for {
          random <- ZIO.random
          id     <- random.nextUUID.map(_.toString)
          price  <- random.nextDoubleBetween(0.0, 100.0)
          item    = Item(id, price)
          key     = Item.itemKey(item)

          redis  <- ZIO.service[Redis]
          service = RedisService(redis)

          before <- redis.get(key).returning[Item]

          execute <- service.cacheOrExecute(key, 10.seconds)(ZIO.succeed(item))
          
          _       <- ZIO.sleep(100.millis)
          
          after   <- redis.get(key).returning[Item]
        } yield assert(before)(isNone) &&
          assert(after)(isSome(equalTo(item))) &&
          assert(execute)(equalTo(item))
      }
  }.provideShared(
    EmbeddedRedis.layer,
    SingleNodeExecutor.layer,
    Redis.layer,
    ZLayer.succeed(ProtobufCodecSupplier)
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.withLiveClock
}
