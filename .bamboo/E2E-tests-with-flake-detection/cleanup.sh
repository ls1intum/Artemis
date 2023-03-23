#!/bin/sh

# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
# show all running docker containers and volumes after the cleanup to detect issues
echo "SHOW RUNNING Docker containers and volumes:"
docker ps -a
docker volume ls
# first kill ALL containers on the bamboo agent
echo "KILLING Docker containers, volumes and networks:"
docker container stop $(docker ps -a -q) || true
docker container rm $(docker ps -a -q) || true
docker volume rm $(docker volume ls -q) || true

# then kill remaining project volumes and networks which should be easy removable as not bound to containers anymore
# for backwards compatibility we check for old docker compose files
if [ -f docker/cypress-E2E-tests.yml ]; then
    cd docker
    docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml down -v
elif [ -f src/main/docker/cypress/cypress-E2E-tests.yml ]; then
    cd src/main/docker/cypress
    docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml down -v
elif [ -f src/main/docker/cypress/docker-compose.yml ]; then
    cd src/main/docker/cypress
    docker compose -f docker-compose.yml -f docker-compose.coverage.yml down -v
fi


# show all running docker containers and volumes after the cleanup to detect issues
echo "SHOW RUNNING Docker containers and volumes:"
docker ps -a
docker volume ls

# show docker and docker compose version
echo "VERSIONS:"
docker compose version || true
docker-compose version || true
docker version || true
