#-----------------------------------------------------------------------------------------------------------------------
# Artemis Dockerfile
#-----------------------------------------------------------------------------------------------------------------------
# Possible ways to build the Docker Image (assuming you are located in the project root direcotry):
#
# using Docker Compose (no cache and pulling latest base images):
# cd docker && docker compose build --no-cache --pull artemis-app
#
# using Docker Compose with an external .war file (no cache and pulling latest base images):
# cd docker && docker compose build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
#
# using Docker (no cache):
# docker build . -f ./docker/artemis/Dockerfile -t ghcr.io/ls1intum/artemis:latest --no-cache
#
# using Docker with an external .war file (no cache):
# docker build --build-arg WAR_FILE_STAGE=external_builder . -f ./docker/artemis/Dockerfile -t ghcr.io/ls1intum/artemis:latest  --no-cache
#-----------------------------------------------------------------------------------------------------------------------

# defines the default .war file build stage (options: builder, external_builder)
ARG WAR_FILE_STAGE="builder"

#-----------------------------------------------------------------------------------------------------------------------
# build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25-jdk AS builder

# some Apple M1 (arm64) builds need python3 and build-essential(make+gcc) for node-gyp to not fail
RUN echo "Installing build dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends python3 build-essential \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/artemis
# copy gradle related files
COPY gradlew gradlew.bat ./
COPY build.gradle gradle.properties settings.gradle ./
COPY gradle gradle/
# copy npm related files and install node modules
# (from https://stackoverflow.com/questions/63961934/how-to-use-docker-build-cache-when-version-bumping-a-react-app)
COPY package.json package-lock.json ./
# also copy this script which is required by postinstall lifecycle hook
COPY src/main/webapp/app/lecture/manage/pdf-preview/pdfjs_copy_worker_script.mjs ./src/main/webapp/app/lecture/manage/pdf-preview/

RUN \
  # Mount global cache for Gradle (project cache in /opt/artemis/.gradle doesn't seem to be populated)
  --mount=type=cache,target=/root/.gradle/caches \
  # Mount cache for npm
  --mount=type=cache,target=/opt/artemis/.npm \
  # Create .npm directory if not yet available
  mkdir -p /opt/artemis/.npm \
  # Set .npm directory as npm cache
  && ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar npmSetCacheDockerfile \
  # Pre-populate the npm and gradle caches if related files change (see COPY statements above)
  && ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar npm_ci

# so far just using the .dockerignore to define what isn't necessary here
COPY . .

RUN \
  # Mount global cache for Gradle (project cache in /opt/artemis/.gradle doesn't seem to be populated)
  --mount=type=cache,target=/root/.gradle/caches \
  # Mount cache for npm
  --mount=type=cache,target=/opt/artemis/.npm \
  # Mount cache for the Angular CLI
  --mount=type=cache,target=/opt/artemis/.cache \
  # Build the .war file
  ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar clean bootWar

#-----------------------------------------------------------------------------------------------------------------------
# external build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/alpine:3.21.1 AS external_builder

#default path of the built .war files
ARG WAR_FILE_PATH="/opt/artemis/build/libs"

# transfer the .war file from the current directory to the default WAR_FILE_PATH
WORKDIR ${WAR_FILE_PATH}
COPY ./build/libs/*.war Artemis.war

#-----------------------------------------------------------------------------------------------------------------------
# war file stage (decides whether an external .war file will be used or the Docker built .war file)
#-----------------------------------------------------------------------------------------------------------------------
FROM ${WAR_FILE_STAGE} AS war_file

#-----------------------------------------------------------------------------------------------------------------------
# runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:25-jdk AS runtime

#default path of the built .war files
ARG WAR_FILE_PATH="/opt/artemis/build/libs"
#default UID/GID of the artemis user
ARG UID=1337
ARG GID=1337

# Docker Compose: wget (healthcheck docker compose)
# Artemis: graphviz, locales
RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends locales graphviz wget \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# See https://github.com/ls1intum/Artemis/issues/4439
RUN \
  echo "Fixing locales" \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen
ENV LC_ALL=en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US.UTF-8

# Create directories for volumes, create artemis user and set right owners
RUN \
  mkdir -p /opt/artemis/config /opt/artemis/data /opt/artemis/public/content \
  && groupadd --gid ${GID} artemis \
  && useradd -m --gid ${GID} --uid ${UID} --shell /bin/bash artemis \
  && chown -R artemis:artemis /opt/artemis
USER artemis:artemis

# Copy Artemis.war to execution location
WORKDIR /opt/artemis

COPY --chown=artemis:artemis --from=war_file ${WAR_FILE_PATH}/*.war Artemis.war

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080

# use exec format (square brackets) as otherwise the shell fromat will not forward signals
CMD [ "java", \
"-Djdk.tls.ephemeralDHKeySize=2048", \
"-DLC_CTYPE=UTF-8", \
"-Dfile.encoding=UTF-8", \
"-Dsun.jnu.encoding=UTF-8", \
"-Djava.security.egd=file:/dev/./urandom", \
"-Xmx5120m", \
"-Xms2560m", \
"--add-modules", "java.se", \
"--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED", \
"--add-exports", "java.naming/com.sun.jndi.ldap=ALL-UNNAMED", \
"--add-opens", "java.base/java.lang=ALL-UNNAMED", \
"--add-opens", "java.base/java.nio=ALL-UNNAMED", \
"--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", \
"--add-opens", "java.management/sun.management=ALL-UNNAMED", \
"--add-opens", "jdk.management/com.sun.management.internal=ALL-UNNAMED", \
"-jar", "/opt/artemis/Artemis.war" ]
