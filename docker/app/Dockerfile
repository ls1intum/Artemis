FROM openjdk:8-jdk-alpine

# setup working directory
RUN mkdir ./app/
WORKDIR ./app/

# install node and yarn
RUN apk add nodejs nodejs-npm
RUN apk add yarn