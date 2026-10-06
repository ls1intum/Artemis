# Central configuration sources details

The docker compose files in this directory don't follow the project wide docker compose file structure as they are
apparently included as bind mounts in `../broker-registry.yml`.

The JHipster-Registry will use the following directories as its configuration source :

- localhost-config : when running the registry in docker with the jhipster-registry.yml docker compose file
- docker-config : when running the registry and the app both in docker with the app.yml docker compose file

For more info, refer to https://www.jhipster.tech/jhipster-registry/#spring-cloud-config
