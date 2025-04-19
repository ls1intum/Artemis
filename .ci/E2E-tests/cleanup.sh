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

docker compose -f ./docker/playwright-E2E-tests-mysql.yml down -v
docker compose -f ./docker/playwright-E2E-tests-multi-node.yml down -v

# show all running docker containers and volumes after the cleanup to detect issues
echo "SHOW RUNNING Docker containers and volumes:"
docker ps -a
docker volume ls

# show docker and docker compose version
echo "VERSIONS:"
docker compose version || true
docker-compose version || true
docker version || true

# Set paths
ARTEMIS_PATH="$(readlink -f "$(dirname "$0")/../..")"
EXEC_FILE="$ARTEMIS_PATH/docker/playwright/report/jacoco-report.exec"
REPORT_TASK="jacocoE2EReport"

# Check if the .exec file exists, with a timeout of 5 seconds
echo "Waiting for JaCoCo .exec file to be generated at: $EXEC_FILE"
for i in {1..5}; do
    if [ -f "$EXEC_FILE" ]; then
        echo "JaCoCo .exec file found: $EXEC_FILE"
        break
    else
        echo ".exec file not yet available. Retrying in 1 second..."
        sleep 1
    fi
done
