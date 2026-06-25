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

# python3/build-essential were previously installed defensively for npm deps that
# trigger node-gyp; no current Artemis dep needs that. If a future dep does, the
# pnpm step below fails loudly and these can be reinstated.

WORKDIR /opt/artemis
# copy gradle related files
COPY gradlew gradlew.bat ./
COPY build.gradle gradle.properties settings.gradle ./
COPY gradle gradle/
# copy pnpm related files and install node modules
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
# patches/ is referenced by pnpm.patchedDependencies in pnpm-workspace.yaml and must
# be present at install time (pnpm applies patches during resolution, not via a
# postinstall hook like patch-package did).
COPY patches patches/
# also copy this script which is required by postinstall lifecycle hook
COPY src/main/webapp/app/lecture/manage/pdf-preview/pdfjs_copy_worker_script.mjs ./src/main/webapp/app/lecture/manage/pdf-preview/

# One Gradle invocation does both the cache config and the pnpm install. The
# BuildKit pnpm-store cache mount below is reused on warm builds; pnpm's
# content-addressable store makes the second build of an unchanged tree
# essentially free.
RUN \
  --mount=type=cache,target=/root/.gradle/caches \
  --mount=type=cache,target=/opt/artemis/.pnpm-store \
  mkdir -p /opt/artemis/.pnpm-store \
  && ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar pnpmSetCacheDockerfile pnpmInstall

# so far just using the .dockerignore to define what isn't necessary here
COPY . .

# `-x pnpmInstall` skips re-running the install task — node_modules is already
# in place from the previous RUN.
#
# `-Psbom` includes the server + client Software Bill of Materials inside the WAR.
# This Dockerfile is the production image build path (used by the `docker-release`
# job in .github/workflows/build.yml, which only fires on push to develop / main /
# release/* / version tags and on release events). Always emitting the SBOM here
# guarantees that deployed images expose the dependency inventory via
# AdminSbomResource and the weekly vulnerability scan finds something to scan.
# The PR Docker image (docker-pr) bypasses this RUN stage via WAR_FILE_STAGE=external_builder
# and reuses the WAR from build-war, which deliberately ships without SBOM on PRs.
RUN \
  --mount=type=cache,target=/root/.gradle/caches \
  --mount=type=cache,target=/opt/artemis/.pnpm-store \
  --mount=type=cache,target=/opt/artemis/.cache \
  ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar -Psbom clean bootWar -x pnpmInstall

#-----------------------------------------------------------------------------------------------------------------------
# external build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/alpine:3.23.4 AS external_builder

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

# Generate an Application Class-Data Sharing (AppCDS) archive at image-build time. The runtime then memory-maps pre-parsed class metadata instead of re-parsing every class on
# each start, which shortens cold start (commonly ~10-25%) with no steady-state cost (unlike -XX:TieredStopAtLevel=1). The training run refreshes the Spring context against an
# in-memory H2 database and exits immediately (-Dspring.context.exit=onRefresh), so it needs no external services. It deliberately omits the buildagent profile, which would
# require a Docker daemon that is unavailable during the image build. AppCDS keys on the classpath + module flags (identical here to the runtime CMD), not on the active Spring
# profiles, so the archive built from this representative profile set is reused at runtime regardless of which profiles a node activates. It is best-effort: on any failure the
# build still succeeds (the archive is simply absent) and the runtime falls back to -XX:+AutoCreateSharedArchive (see CMD), so a missing or stale archive never blocks startup.
RUN java -XX:ArchiveClassesAtExit=/opt/artemis/Artemis.jsa \
  -Dspring.context.exit=onRefresh \
  -Xmx3g \
  --add-modules java.se \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.management/sun.management=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  -jar /opt/artemis/Artemis.war \
  --spring.profiles.active=dev,artemis,core,scheduling,localci,localvc,ldap \
  --spring.datasource.url='jdbc:h2:mem:cdstraining;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.username=sa --spring.datasource.password= \
  --eureka.client.enabled=false \
  || echo "WARNING: AppCDS training run failed; the image will start without a baked archive (runtime falls back to -XX:+AutoCreateSharedArchive)."

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080

# use exec format (square brackets) as otherwise the shell fromat will not forward signals
# -XX:SharedArchiveFile uses the AppCDS archive baked above; -XX:+AutoCreateSharedArchive turns a missing/stale archive into a no-op (regenerated at exit) instead of a startup failure.
CMD [ "java", \
"-XX:+AutoCreateSharedArchive", \
"-XX:SharedArchiveFile=/opt/artemis/Artemis.jsa", \
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
