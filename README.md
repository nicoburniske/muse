# Muse

<img src="https://github.com/nicoburniske/muse-frontend/blob/main/public/logo.png" width="350" title="Muse Logo">

A place for music lovers to create interactive and collaborative reviews for music on Spotify.

> Frontend Code is available here https://github.com/nicoburniske/muse-frontend.

> Currently in Open Beta.

## Features

- Purely Functional with Scala 3 & [ZIO 2](https://github.com/zio/zio)
- Compile Time Postgres SQL Query Generation with [Quill](https://github.com/zio/zio-quill)
- GraphQL Server with [Caliban](https://github.com/ghostdogpr/caliban)
  and [ZIO Http](https://github.com/dream11/zio-http)
    - Compile time GraphQL Schema Generation
    - GraphQL Schema Introspection and Apollo Tracing are supported
    - See [Muse Schema](https://github.com/nicoburniske/muse/tree/main/src/main/resources/graphql/schema.graphql)
- GraphQL Query Batching Optimization with [ZIO Query](https://github.com/zio/zio-query)
- [Spotify API](https://developer.spotify.com/documentation/web-api/) Client written in Tagless Final
  with [sttp client](https://github.com/softwaremill/sttp)
- GraphQL Subscriptions enable Real time review updates using ZStream
   - User current playback state
   - Review updates (created/deleted/updated comments)
- Caching and rate-limiting done with [ZIO-Redis](https://github.com/zio/zio-redis)
- Events broadcasted using [NATS](https://github.com/nats-io) 
