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
CypressDockerComposeFilePath=""
if [ -f docker/cypress-E2E-tests.yml ]; then
    CypressDockerComposeFilePath="docker/cypress-E2E-tests.yml"
elif [ -f src/main/docker/cypress/cypress-E2E-tests.yml ]; then
    CypressDockerComposeFilePath="src/main/docker/cypress/cypress-E2E-tests.yml"
elif [ -f src/main/docker/cypress/docker-compose.yml ]; then
    CypressDockerComposeFilePath="src/main/docker/cypress/docker-compose.yml"
fi
docker compose -f $CypressDockerComposeFilePath down -v

# show all running docker containers and volumes after the cleanup to detect issues
echo "SHOW RUNNING Docker containers and volumes:"
docker ps -a
docker volume ls

# show docker and docker compose version
echo "VERSIONS:"
docker compose version || true
docker-compose version || true
docker version || true
