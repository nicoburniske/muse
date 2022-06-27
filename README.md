# Muse: A Social Platform for Spotify

## Introduction

## Getting Started

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Change permissions on `start.sh` and `stop.sh`

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

## Functionality

- Purely Functional with Scala 3 & [ZIO 2.0](https://github.com/zio/zio)
- Compile Time Postgres SQL Query Generation with [Quill](https://github.com/zio/zio-quill)
- GraphQL Server with [Caliban](https://github.com/ghostdogpr/caliban)
    - Compile time GraphQL Schema Generation
    - See [Muse Schema](https://github.com/nicoburniske/muse/tree/main/src/main/resources/schema.graphql)
- GraphQL Query Optimization with [ZIO Query](https://github.com/zio/zio-query)
- [Spotify API](https://developer.spotify.com/documentation/web-api/) Client written in Tagless Final
    - Using [sttp client](https://github.com/softwaremill/sttp)
    - [See Source](https://github.com/nicoburniske/muse/tree/main/src/main/scala/muse/service/spotify/SpotifyAPI.scala)


## TODO
  - Extract Spotify API to own module 
  - GraphQL Review Subscriptions
