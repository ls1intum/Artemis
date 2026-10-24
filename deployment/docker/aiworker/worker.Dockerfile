# Build the normal Artemis WAR first, then pass its exact path as ARTEMIS_WAR.
FROM docker.io/library/eclipse-temurin:25.0.4.1_1-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc
ARG ARTEMIS_WAR
WORKDIR /opt/aiworker
COPY --chown=1000:1000 ${ARTEMIS_WAR} worker.war
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/opt/aiworker/worker.war", "--spring.profiles.active=prod,aiworker"]
