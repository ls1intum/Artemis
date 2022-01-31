FROM openjdk:17-jdk-alpine

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

## Add a jhipster user to run our application so that it doesn't need to run as root
RUN adduser -D -s /bin/sh artemis
WORKDIR /home/artemis/user-management
RUN touch application-dev.yml application-prod.yml application-artemis.yml
USER artemis

ARG WAR_PATH=user-management/build/libs
COPY $WAR_PATH/*.war user-management.war

ENTRYPOINT ["java","-jar","user-management.war"]

EXPOSE 8087
