package muse.service.event

import io.nats.client.{Connection, Message, Nats}
import muse.config.NatsConfig
import zio.*
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, DecodeError, ProtobufCodec}
import zio.stream.*

trait EventService {
  def publish[E: Schema](subject: String, event: E): UIO[Boolean]
  def subscribe[E: Schema](subject: String): ZIO[Scope, Throwable, Stream[DecodeError, E]]
}

trait EventCodecSupplier {
  def get[E: Schema]: BinaryCodec[E]
}

object EventService {
  val layer = ZLayer.fromFunction(EventServiceLive(_, _))

  val natsLayer = ZLayer
    .fromZIO {
      for {
        natsConfig <- ZIO.service[NatsConfig]
        connection <- ZIO.attempt(Nats.connect(natsConfig.url))
      } yield connection
    }.tap { c => ZIO.logInfo(s"Connected to NATS server at ${c.get.getConnectedUrl}") }.tapErrorCause { e =>
      ZIO.logErrorCause(s"Failed to connect to NATS server", e)
    }

  val codecLayer = ZLayer.succeed(new EventCodecSupplier {
    def get[E: Schema]: BinaryCodec[E] = ProtobufCodec.protobufCodec
  })
}

final case class EventServiceLive(connection: Connection, codec: EventCodecSupplier) extends EventService {
  override def publish[E: Schema](subject: String, event: E) =
    ZIO.attempt(connection.publish(subject, codec.get.encode(event).toArray)).isSuccess

  override def subscribe[E: Schema](subject: String) =
    ZIO
      .acquireRelease {
        ZIO.attempt(connection.createDispatcher())
      } { dispatcher => ZIO.attempt(dispatcher.unsubscribe(subject)).ignore }.map { dispatcher =>
        ZStream.async[Any, DecodeError, E] { cb => dispatcher.subscribe(subject, message => cb(decode(message))) }
      }

  private def decode[E: Schema](message: Message): IO[Option[DecodeError], Chunk[E]] =
    codec.get.decode(Chunk.fromArray(message.getData)) match
      case Left(value)  => ZIO.fail(Some(value))
      case Right(value) => ZIO.succeed(Chunk(value))

}
