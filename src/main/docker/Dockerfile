FROM openjdk:17-jdk-slim-bullseye

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

# Add an artemis user to run our application so that it doesn't need to run as root
RUN adduser artemis
WORKDIR /home/artemis
USER artemis

ARG WAR_PATH=build/libs
COPY $WAR_PATH/*.war Artemis.war

ENTRYPOINT ["java","-jar","Artemis.war"]

EXPOSE 8080
