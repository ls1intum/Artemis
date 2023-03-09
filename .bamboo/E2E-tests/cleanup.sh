#!/bin/sh

cd src/main/docker/cypress

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker-compose -f cypress-E2E-tests.yml stop -t 60
docker-compose -f cypress-E2E-tests.yml rm -v -f
# The following docker compose command could usually replace the commands above. But as we don't know the internals
# of the commands we use both to make this more robust and "really" stop and remove all containers, volumes and networks
docker-compose -f cypress-E2E-tests.yml down -v -t 60
