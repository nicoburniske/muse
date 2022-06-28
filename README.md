# Muse: A Social Platform for Spotify

## Features

- Purely Functional with Scala 3 & [ZIO 2.0](https://github.com/zio/zio)
- Compile Time Postgres SQL Query Generation with [Quill](https://github.com/zio/zio-quill)
- GraphQL Server with [Caliban](https://github.com/ghostdogpr/caliban)
  and [ZIO Http](https://github.com/dream11/zio-http)
    - Compile time GraphQL Schema Generation
    - GraphQL Schema Introspection and Apollo Tracing are supported
    - See [Muse Schema](https://github.com/nicoburniske/muse/tree/main/src/main/resources/schema.graphql)
- GraphQL Query Optimization with [ZIO Query](https://github.com/zio/zio-query)
- [Spotify API](https://developer.spotify.com/documentation/web-api/) Client written in Tagless Final
  with [sttp client](https://github.com/softwaremill/sttp)
    - [See Source](https://github.com/nicoburniske/muse/tree/main/src/main/scala/muse/service/spotify/SpotifyAPI.scala)

## Getting Started

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Make scripts `start.sh` and `stop.sh` executable in terminal

> chmod +x start.sh
>
> chmod +x stop.sh

3. Create API Keys in [Spotify API Developer Dashboard](https://developer.spotify.com/dashboard/login)
    1. Create an App
    2. Find Client ID and Client Secret
        1. Option 1: Input directly
           into [application.conf](https://github.com/nicoburniske/muse/tree/main/src/main/resources/application.conf)
        2. Option 2: Set `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` as System/Environment variables
            1. Put into docker-compose.yaml -> backend -> environment
4. Run `./start.sh` in terminal
    1. Sets up local PSQL Instance and Backend Instance
    2. See [initialization file](https://github.com/nicoburniske/muse/tree/main/src/main/resources/sql/init.sql) for
       more information
    3. Http server will start running at `localhost:8883`
5. Login with Spotify Account @ `localhost:8883/login`
6. After successful login backend will redirect to localhost:3000 you should have a session cookie in browser
7. To use Graphql API (`localhost:8883/api/graphql`) download a GraphQL Client (e.g. GraphQL Playground)
8. Add an Authorization Header. The value should be the cookie value. Below is an example for GraphQL Playground (**HTTP
   Headers** Panel)
   ```json
    {
      "Authorization" : "YourSessionCookie"
    }
   ```
9. Schema Introspection should now be enabled. You can now make GraphQL queries to the backend.


## TODO

- GraphQL Review Subscriptions
- Readme Introduction
- Extract Spotify API to own module 
