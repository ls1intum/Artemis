#!/bin/sh

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker-compose -f ./src/main/docker/cypress-E2E-tests.yml down -v

