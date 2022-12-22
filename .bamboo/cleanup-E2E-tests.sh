# HOST_HOSTNAME not really necessary for shutdown but otherwise docker-compose complains
export HOST_HOSTNAME=$(hostname)
docker-compose --env-file .src/main/docker/cypress/bamboo-E2E-testing.env down -v
