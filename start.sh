#!/bin/bash

# If you want logs
#docker-compose down && docker-compose up
# If you don't want logs
docker-compose down && docker-compose up -d && docker ps