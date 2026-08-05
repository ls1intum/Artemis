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
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25.0.3_9-jdk AS builder

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
# pnpm resolves the `workspace:*` dependencies while installing, so every workspace member's
# manifest has to exist by then. Manifests only: copying sources here would invalidate the cached
# install layer on every package edit. A new member without its own COPY aborts the install.
COPY packages/tum-ui/package.json packages/tum-ui/

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
# WAR_FILE_STAGE resolves to a build stage defined above (builder / external_builder), not an
# external image, so there is no version tag to pin (DL3006 is a false positive here).
# hadolint ignore=DL3006
FROM ${WAR_FILE_STAGE} AS war_file

#-----------------------------------------------------------------------------------------------------------------------
# runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:25.0.3_9-jdk AS runtime

#default path of the built .war files
ARG WAR_FILE_PATH="/opt/artemis/build/libs"
#default UID/GID of the artemis user
ARG UID=1337
ARG GID=1337

# Docker Compose: wget (healthcheck docker compose)
# Artemis: graphviz, locales
# Not pinning apt versions (DL3008): these are base-image system packages whose pinned versions age
# out of the Debian repos and would break the image build over time.
# hadolint ignore=DL3008
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

RUN set -eu; \
  war_entries="$(jar tf Artemis.war)"; \
  if ! printf '%s\n' "$war_entries" | grep -qx 'WEB-INF/classes/static/index.html'; then \
    echo "Artemis.war does not contain WEB-INF/classes/static/index.html. The Angular client is missing from the deployable WAR."; \
    exit 1; \
  fi; \
  if ! printf '%s\n' "$war_entries" | grep -Eq '^WEB-INF/classes/static/main-.*\.js$'; then \
    echo "Artemis.war does not contain a WEB-INF/classes/static/main-*.js bundle. The Angular client build is incomplete."; \
    exit 1; \
  fi

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080 5701 7921

# Mirrors docker/artemis.yml's compose-level healthcheck so `docker run`/`docker ps` report
# health even for anyone running the raw image directly, outside the provided compose files.
# When run via compose, the service's own `healthcheck:` block there takes precedence over this.
HEALTHCHECK --interval=1s --timeout=5s --start-period=600s --retries=3 \
  CMD wget -q -t1 -T5 -O - http://localhost:8080/management/health/readiness | grep -xq '{"status":"UP"}' || exit 1

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
