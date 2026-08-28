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
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5 AS builder

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
# job in .github/workflows/ci-build.yml, which only fires on push to develop / main /
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
FROM docker.io/library/alpine:3.23.5@sha256:fd791d74b68913cbb027c6546007b3f0d3bc45125f797758156952bc2d6daf40 AS external_builder

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
# extract stage (explode the WAR so the runtime image runs from a flat classpath -> faster startup)
#-----------------------------------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5 AS extractor

#default path of the built .war files
ARG WAR_FILE_PATH="/opt/artemis/build/libs"

WORKDIR /extract
COPY --from=war_file ${WAR_FILE_PATH}/*.war Artemis.war
# `jarmode=tools extract` produces app/Artemis.war (thin) + app/lib/ with a flat Class-Path. Running
# from this exploded layout avoids Spring Boot's nested-jar classloader, which dominates cold start
# (~38s -> ~25s measured). The extraction cost is paid once here at image-build time, so every
# container start is fast with no runtime extraction.
RUN java -Djarmode=tools -jar Artemis.war extract --destination /extract/app

#-----------------------------------------------------------------------------------------------------------------------
# runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5 AS runtime

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

# Copy the exploded Artemis layout (app/Artemis.war + app/lib/) to the execution location.
# WORKDIR stays /opt/artemis so Spring still discovers config from /opt/artemis and /opt/artemis/config.
WORKDIR /opt/artemis

COPY --chown=artemis:artemis --from=extractor /extract/app /opt/artemis/app

# Verify the exploded runtime artifact actually contains a fully built Angular client.
# `jarmode=tools extract` flattens the WAR: the former WEB-INF/classes/** content (including static/)
# is hoisted to the root of the thin app/Artemis.war, so the client lives at static/** here — every
# entry name below is relative to that flattened root, not to WEB-INF/classes/.
# The Tailwind directive list is a denylist: revisit it whenever Tailwind gains one. The same
# invariant is asserted for the package stylesheet in packages/tum-ui/check-styles.mjs.
# `jar` prefix-matches entry names, so every stylesheet arrives together with its .css.map, and that
# map embeds the original source, directives and all. Only the .css files are scanned.
RUN set -eu; \
  war_entries="$(jar tf app/Artemis.war)"; \
  if ! printf '%s\n' "$war_entries" | grep -qx 'static/index.html'; then \
    echo "app/Artemis.war does not contain static/index.html. The Angular client is missing from the deployable WAR."; \
    exit 1; \
  fi; \
  if ! printf '%s\n' "$war_entries" | grep -Eq '^static/main-.*\.js$'; then \
    echo "app/Artemis.war does not contain a static/main-*.js bundle. The Angular client build is incomplete."; \
    exit 1; \
  fi; \
  stylesheets="$(printf '%s\n' "$war_entries" | grep -E '^static/[^/]+\.css$' || true)"; \
  if [ -z "$stylesheets" ]; then \
    echo "app/Artemis.war contains no static/*.css bundle. The Angular client build is incomplete."; \
    exit 1; \
  fi; \
  extracted="$(mktemp -d)"; \
  printf '%s\n' "$stylesheets" | while read -r stylesheet; do jar xf app/Artemis.war -C "$extracted" "$stylesheet"; done; \
  if [ "$(find "$extracted" -name '*.css' | wc -l)" -ne "$(printf '%s\n' "$stylesheets" | wc -l)" ]; then \
    echo "Could not extract the stylesheet bundles from app/Artemis.war; the Tailwind check did not run."; \
    rm -rf "$extracted"; \
    exit 1; \
  fi; \
  uncompiled="$(find "$extracted" -name '*.css' -exec grep -hoE '@(tailwind|source|theme|plugin|custom-variant|utility|variant|apply|reference|config)[[:space:]("{]' {} + | sed 's/.$//' | sort -u | paste -sd' ' - || true)"; \
  rm -rf "$extracted"; \
  if [ -n "$uncompiled" ]; then \
    echo "app/Artemis.war ships a client stylesheet that still contains Tailwind source directives: ${uncompiled}."; \
    echo "The Tailwind PostCSS plugin did not transform it, so no utility class in the templates resolves to a rule and the application renders unstyled."; \
    echo "Check that a PostCSS config at the repository root names the plugin and that the build which produced this WAR could read it; for an in-Docker build that means .dockerignore."; \
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
"-jar", "/opt/artemis/app/Artemis.war" ]
