# Muse: Spotify Social Platform

## Introduction

## Getting Started

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Change permissions on start.sh and stop.sh

> chmod +x start.sh
>
> chmod +x stop.sh

3. Run `./start.sh` in terminal
    1. This sets up a local PSQL Database
    2. See [initialization file](https://github.com/nicoburniske/muse/tree/master/src/main/resources/sql/init.sql) for
       more information
4. Run `sbt run`
    1. Http server will start running