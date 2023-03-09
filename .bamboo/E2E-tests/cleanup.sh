#!/bin/sh

cd src/main/docker/cypress

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker-compose -f cypress-E2E-tests.yml stop -t 60
docker-compose -f cypress-E2E-tests.yml rm -v -f
