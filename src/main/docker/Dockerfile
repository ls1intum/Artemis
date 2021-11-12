FROM openjdk:17-jdk-alpine

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

# Add an artemis user to run our application so that it doesn't need to run as root
RUN adduser -D -s /bin/sh artemis
WORKDIR /home/artemis
USER artemis

COPY build/libs/*.war Artemis.war

ENTRYPOINT ["java","-jar","Artemis.war"]

EXPOSE 8080
