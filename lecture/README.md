This is a "microservice" application intended to be part of the Artemis architecture.

## Environment preparation

This application is configured for [Service Discovery and Configuration with the JHipster-Registry](https://www.jhipster.tech/documentation-archive/v7.1.0/microservices-architecture/#jhipster-registry). On launch, it will refuse to start if it is not able to connect to the JHipster-Registry at [http://localhost:8761](http://localhost:8761).
You can start the JHipster Registry using Docker, by running:
```bash
docker-compose -f src/main/docker/app.yml up jhipster-registry
```

or you can clone it from the [GitHub repository of the JHipster Registry](https://github.com/jhipster/jhipster-registry).

Important part for the connection is to make sure that `jhipster.registry.password` in `application-dev.yml/application-prod.yml` and
`JHIPSTER_REGISTRY_PASSWORD` in the Docker Compose file are the same. If not the Gateway will not be able to register to the Registry.

The JHipster Registry is a Spring Cloud Config server and holds the configuration for the applications.
When application (gateway or microservice) connects to the registry it gets configuration data which should be shared among all of the application.
An example is `base64-secret` used to verify JWT tokens. It can be changed in `docker/central-server-config/application.yml`.

**It is recommended to change the base64 secret and the JHipster Registry password for the production environment.**

## Development

To start your application in the dev profile, run:

```bash
./gradlew :lecture:bootRun --args='--spring.profiles.active=dev,artemis'
```

## Building for production

### Packaging as jar

To build the final jar and optimize the userManagement application for production, run:

```bash
./gradlew -Pprod clean :lecture:bootJar
```

To ensure everything worked, run:

```bash
java -jar build/libs/*.jar
```

### Packaging as war

To package your application as a war in order to deploy it to an application server, run:

```bash
./gradlew -Pprod -Pwar clean :lecture:bootWar
```

## Testing

To launch your application's tests, run:

```bash
./gradlew :lecture:executeTests :lecture:jacocoTestReport
```

## Using Docker to simplify development (optional)

You can use Docker to improve your JHipster development experience. A number of docker-compose configurations are available in the [src/main/docker](src/main/docker) folder to launch required third-party services.

You can also fully dockerize your application and all the services that it depends on.
To achieve this, first build a docker image of your app by running:

```bash
./gradlew bootJar -Pprod jibDockerBuild
```

Then run:

```bash
docker-compose -f src/main/docker/app.yml up -d
```
