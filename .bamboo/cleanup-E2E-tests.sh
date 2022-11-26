cd src/main/docker/cypress

docker-compose --env-file ./bamboo-E2E-testing.env stop
docker-compose --env-file ./bamboo-E2E-testing.env rm -f
