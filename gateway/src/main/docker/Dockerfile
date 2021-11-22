FROM openjdk:17-jdk-alpine

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

# Add a jhipster user to run our application so that it doesn't need to run as root
RUN adduser -D -s /bin/sh artemis
WORKDIR /home/artemis/gateway
USER artemis

ARG WAR_PATH=gateway/build/libs
COPY $WAR_PATH/*.war gateway.war

ENTRYPOINT ["java","-jar","gateway.war"]

EXPOSE 8089
