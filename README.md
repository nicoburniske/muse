# Muse: Spotify Social Platform

## Introduction

## Getting Started

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Change permissions on `start.sh` and `stop.sh`

> chmod +x start.sh
>
> chmod +x stop.sh

3. Run `./start.sh` in terminal
    1. This sets up a local PSQL Database
    2. See [initialization file](https://github.com/nicoburniske/muse/tree/master/src/main/resources/sql/init.sql) for
       more information
4. Run `sbt run`
    1. Http server will start running

## Functionality
- Purely Functional with [ZIO 2.0](https://github.com/zio/zio)
- Compile Time Postgres SQL Query Generation with [Quill](https://github.com/zio/zio-quill)
- GraphQL Server with [Caliban](https://github.com/ghostdogpr/caliban) 
- GraphQL Query Optimization with [ZIO Query](https://github.com/zio/zio-query)
- [Spotify API](https://developer.spotify.com/documentation/web-api/) Client written in Tagless Final 
  - [See Source](https://github.com/nicoburniske/muse/tree/master/src/main/scala/muse/service/spotify/SpotifyAPI.scala)
  - Leverages [sttp client](https://github.com/softwaremill/sttp) 


## TODO
  - Extract Spotify API to own module 
  - GraphQL Review Subscriptions