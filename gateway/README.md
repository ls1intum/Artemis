# Gateway
The Gateway is a JHipster application of application type gateway. It serves the client application and handles the Web traffic. 
It will automatically proxy all requests sent to the microservices. The gateway also takes care for user authorization if such rules are defined.
It can permit or deny requests if JWT is not available or cannot be verified. The JWT (if it exists) is also attached to the requests sent to the microservices,
so it is checked by the microservice as well.

## Environment preparation

This application is configured for Service Discovery and Configuration with the JHipster-Registry. On launch, it will refuse to start if it is not able to connect to the JHipster-Registry at [http://localhost:8761](http://localhost:8761).
You can start the JHipster Registry using Docker, by running:
```bash
docker-compose -f src/main/docker/jhipster-registry.yml up
```

or you can clone it from the [GitHub repostory of the JHipster Registry](https://github.com/jhipster/jhipster-registry).

Important part for the connection is to make sure that `jhipster.registry.password` in `application-dev.yml/application-prod.yml` and 
`JHIPSTER_REGISTRY_PASSWORD` in the Docker Compose file are the same. If not the Gateway will not be able to register to the Registry.

The JHipster Registry is a Spring Cloud Config server and holds the configuration for the applications. 
When application (gateway or microservice) connects to the registry it gets configuration data which should be shared among all of the application.
An example is `base64-secret` used to verify JWT tokens. It can be changed in `docker/central-server-config/docker-config/application.yml` for production environments or 
`docker/central-server-config/localhost-config/application.yml` for development environments.

**It is recommended to change the base64 secret and the JHipster Registry password for production environment.**

## Development

To start your application in the dev profile, run:

```bash
./gradlew :gateway:bootRun
```

### Doing API-First development using openapi-generator

[OpenAPI-Generator][] is configured for this application. You can generate API code from the `src/main/resources/swagger/api.yml` definition file by running:

```bash
./gradlew :gateway:openApiGenerate
```

Then implements the generated delegate classes with `@Service` classes.

To edit the `api.yml` definition file, you can use a tool such as [Swagger-Editor][]. Start a local instance of the swagger-editor using docker by running: `docker-compose -f src/main/docker/swagger-editor.yml up -d`. The editor will then be reachable at [http://localhost:7742](http://localhost:7742).

Refer to [Doing API-First development][] for more details.

## Building for production

### Packaging as jar

To build the final jar and optimize the gateway application for production, run:

```bash
./gradlew -Pprod clean :gateway:bootJar
```

This will concatenate and minify the client CSS and JavaScript files. It will also modify `index.html` so it references these new files.
To ensure everything worked, run:

```bash
java -jar build/libs/*.jar
```

Then navigate to [http://localhost:8089](http://localhost:8089) in your browser.

Refer to [Using JHipster in production][] for more details.

### Packaging as war

To package your application as a war in order to deploy it to an application server, run:

```bash
./gradlew -Pprod -Pwar clean :gateway:bootWar
```

## Testing

To launch your application's tests, run:

```bash
./gradlew :gateway:executeTests :gateway:jacocoTestReport -x webapp -x copyClientBuildFiles
```

## Using Docker to simplify development (optional)

You can use Docker to improve your JHipster development experience. A number of docker-compose configuration are available in the `src/main/docker` folder to launch required third party services.

You can also fully dockerize your application and all the services that it depends on.
To achieve this, first build a docker image of your app by running:

```bash
./gradlew bootJar -Pprod :gateway:jibDockerBuild
```

Then run:

```bash
docker-compose -f src/main/docker/app.yml up -d
```

## Continuous Integration (optional)

To configure CI for your project, run the ci-cd sub-generator (`jhipster ci-cd`), this will let you generate configuration files for a number of Continuous Integration systems. Consult the [Setting up Continuous Integration][] page for more information.

## Monitoring

Docker Compose files are provided for Prometheus and Grafana. The configuration is intended for development, therefore it
must be hardened for production environments.

**In case you are using MacOS, you need to modify the `docker/monitoring.yml` and `docker/prometheus/prometheus.yml` by removing
`network_mode` and replacing `localhost`.**

[doing microservices with jhipster]: https://www.jhipster.tech/documentation-archive/v7.1.0/microservices-architecture/
[using jhipster in production]: https://www.jhipster.tech/documentation-archive/v7.1.0/production/
[setting up continuous integration]: https://www.jhipster.tech/documentation-archive/v7.1.0/setting-up-ci/
[openapi-generator]: https://openapi-generator.tech
[swagger-editor]: https://editor.swagger.io
[doing api-first development]: https://www.jhipster.tech/documentation-archive/v7.1.0/doing-api-first-development/
