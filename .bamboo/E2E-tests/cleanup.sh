#!/bin/sh

cd docker

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker compose -f cypress-E2E-tests.yml down -v
