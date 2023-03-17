#!/bin/sh

cd docker/cypress

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml down -v
