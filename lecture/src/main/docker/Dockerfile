FROM openjdk:17-jdk-alpine

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

## Add a jhipster user to run our application so that it doesn't need to run as root
RUN adduser -D -s /bin/sh artemis
WORKDIR /home/artemis/lecture
RUN touch application-dev.yml application-prod.yml application-artemis.yml
USER artemis

ARG WAR_PATH=lecture/build/libs
COPY $WAR_PATH/*.war lecture.war

ENTRYPOINT ["java","-jar","lecture.war"]

EXPOSE 8086
