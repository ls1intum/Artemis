#-----------------------------------------------------------------------------------------------------------------------
# Artemis Dockerfile
#-----------------------------------------------------------------------------------------------------------------------
# Possible ways to build the Docker Image (assuming you are located in the project root direcotry):
#
# using Docker Compose (no cache and pulling latest base images):
# cd deployment/docker && docker compose build --no-cache --pull artemis-app
#
# using Docker Compose with an external .war file (no cache and pulling latest base images):
# cd deployment/docker && docker compose build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
#
# using Docker (no cache):
# docker build . -f ./deployment/docker/artemis/Dockerfile -t ghcr.io/ls1intum/artemis:latest --no-cache
#
# using Docker with an external .war file (no cache):
# docker build --build-arg WAR_FILE_STAGE=external_builder . -f ./deployment/docker/artemis/Dockerfile -t ghcr.io/ls1intum/artemis:latest  --no-cache
#-----------------------------------------------------------------------------------------------------------------------

# defines the default .war file build stage (options: builder, external_builder)
ARG WAR_FILE_STAGE="builder"
ARG ARTEMIS_ROLE="universal"
ARG RUNTIME_STAGE="runtime_${ARTEMIS_ROLE}"

#-----------------------------------------------------------------------------------------------------------------------
# build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25.0.5_8-jdk@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d AS builder
ARG ARTEMIS_ROLE

# The downloaded Node 26 ARM64 binary links libatomic.so.1, which the JDK base image
# does not include. Install it before Gradle bootstraps pnpm.
# hadolint ignore=DL3008
RUN apt-get update \
  && apt-get install -y --no-install-recommends libatomic1 \
  && rm -rf /var/lib/apt/lists/*

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
# config/pnpm/patches/ is referenced by pnpm.patchedDependencies in pnpm-workspace.yaml and must
# be present at install time (pnpm applies patches during resolution, not via a
# postinstall hook like patch-package did).
COPY config/pnpm/patches config/pnpm/patches/
# pnpm resolves the `workspace:*` dependencies while installing, so every workspace member's
# manifest has to exist by then. Manifests only: copying sources here would invalidate the cached
# install layer on every package edit. A new member without its own COPY aborts the install.
COPY packages/tum-aet-ui/package.json packages/tum-aet-ui/

# One Gradle invocation does both the cache config and the pnpm install. The
# BuildKit pnpm-store cache mount below is reused on warm builds; pnpm's
# content-addressable store makes the second build of an unchanged tree
# essentially free.
RUN \
  --mount=type=cache,target=/root/.gradle/caches \
  --mount=type=cache,target=/opt/artemis/.pnpm-store \
  mkdir -p /opt/artemis/.pnpm-store \
  && case "$ARTEMIS_ROLE" in universal|core) ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar pnpmSetCacheDockerfile pnpmInstall ;; buildagent|aiworker) : ;; *) exit 1 ;; esac

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
  set -eu; \
  case "$ARTEMIS_ROLE" in \
    universal) task=bootWar ;; core) task=bootCoreWar ;; \
    buildagent) task=bootBuildAgentWar ;; aiworker) task=bootAiWorkerWar ;; *) exit 1 ;; \
  esac; \
  ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar -Psbom clean "$task" -x pnpmInstall; \
  if [ "$ARTEMIS_ROLE" = universal ]; then set -- build/libs/Artemis-*.war; else set -- "build/role-wars/Artemis-${ARTEMIS_ROLE}.war"; fi; \
  [ "$#" = 1 ] && cp "$1" /opt/artemis/Artemis.war

#-----------------------------------------------------------------------------------------------------------------------
# external build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/alpine:3.24.4@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS external_builder

# Existing local scripts supply the versioned universal WAR in build/libs.
# CI supplies an exact filename when packaging a role WAR.
ARG PREBUILT_WAR="build/libs/*.war"
COPY ${PREBUILT_WAR} /opt/artemis/Artemis.war

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
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25.0.5_8-jdk@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d AS extractor
ARG ARTEMIS_ROLE

WORKDIR /extract
COPY --from=war_file /opt/artemis/Artemis.war Artemis.war
# `jarmode=tools extract` produces app/Artemis.jar (thin) + app/lib/ with a flat Class-Path. Running
# from this exploded layout avoids Spring Boot's nested-jar classloader, which dominates cold start
# (~38s -> ~25s measured). The extraction cost is paid once here at image-build time, so every
# container start is fast with no runtime extraction.
# Spring Boot 4.1 wrote the thin archive as app/Artemis.war, Spring Boot 4.2 writes app/Artemis.jar. The check
# names the file, because a missing one otherwise only shows as a NoSuchFileException in a later stage.
RUN set -eu; \
  case "$ARTEMIS_ROLE" in universal) title=Artemis ;; core|buildagent|aiworker) title="Artemis $ARTEMIS_ROLE" ;; *) exit 1 ;; esac; \
  jar xf Artemis.war META-INF/MANIFEST.MF; \
  if ! tr -d '\r' < META-INF/MANIFEST.MF | grep -Fxq "Implementation-Title: $title"; then \
    echo "The supplied WAR does not match ARTEMIS_ROLE=$ARTEMIS_ROLE" >&2; exit 1; \
  fi; \
  java -Djarmode=tools -jar Artemis.war extract --destination /extract/app; \
  if [ ! -f /extract/app/Artemis.jar ]; then \
    echo "jarmode=tools extract did not produce app/Artemis.jar. It produced:"; \
    ls -l /extract/app; \
    exit 1; \
  fi

#-----------------------------------------------------------------------------------------------------------------------
# runtime JDK stage (downloads the JDK that the runtime image runs on)
#-----------------------------------------------------------------------------------------------------------------------
# The runtime uses Oracle JDK 25.0.5.1 until a Temurin 25 image of the same update release is published.
# The archive is pinned by version and verified against Oracle's published SHA-256 checksum.
FROM docker.io/library/ubuntu:26.04@sha256:d66b366dac61b575bee2aef07e9c26e8070096fbe84a3d4d3eb0da069b5d76db AS runtime_jdk

ARG TARGETARCH
ARG JDK_VERSION=25.0.5.1
ARG JDK_SHA256_AMD64=3d40d69461200594380ac84c17be6224539022028730cffe043ac70c41ffd1d5
ARG JDK_SHA256_ARM64=2c30d5fe69796452732d32e746c5f4b4031e4991e2be9a3313f76e51fe5196ee

# hadolint ignore=DL3008
RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

RUN case "${TARGETARCH}" in \
    amd64) jdk_arch=x64; jdk_sha256="${JDK_SHA256_AMD64}" ;; \
    arm64) jdk_arch=aarch64; jdk_sha256="${JDK_SHA256_ARM64}" ;; \
    *) echo "Unsupported architecture: ${TARGETARCH}" >&2; exit 1 ;; \
  esac \
  && curl -fsSL -o /tmp/jdk.tar.gz "https://download.oracle.com/java/25/archive/jdk-${JDK_VERSION}_linux-${jdk_arch}_bin.tar.gz" \
  && echo "${jdk_sha256}  /tmp/jdk.tar.gz" | sha256sum -c - \
  && mkdir -p /opt/java/openjdk \
  && tar -xzf /tmp/jdk.tar.gz -C /opt/java/openjdk --strip-components=1 \
  && rm /tmp/jdk.tar.gz

#-----------------------------------------------------------------------------------------------------------------------
# runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/ubuntu:26.04@sha256:d66b366dac61b575bee2aef07e9c26e8070096fbe84a3d4d3eb0da069b5d76db AS runtime_base

# Same location as in the Temurin images, so that JAVA_HOME does not change for anything that relies on it.
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=runtime_jdk /opt/java/openjdk /opt/java/openjdk

#default UID/GID of the artemis user
ARG UID=1337
ARG GID=1337

# Docker Compose: wget (healthcheck docker compose)
# Artemis: graphviz, locales
# JDK: ca-certificates, fontconfig, tzdata (the Temurin base image shipped them)
# Not pinning apt versions (DL3008): these are base-image system packages whose pinned versions age
# out of the Debian repos and would break the image build over time.
# hadolint ignore=DL3008
RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends locales graphviz wget ca-certificates fontconfig tzdata \
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

# Copy the exploded Artemis layout (app/Artemis.jar + app/lib/) to the execution location.
# WORKDIR stays /opt/artemis so Spring still discovers config from /opt/artemis and /opt/artemis/config.
WORKDIR /opt/artemis

COPY --chown=artemis:artemis --from=extractor /extract/app /opt/artemis/app

# Verify the exploded runtime artifact actually contains a fully built Angular client.
# `jarmode=tools extract` flattens the WAR: the former WEB-INF/classes/** content (including static/)
# is hoisted to the root of the thin app/Artemis.jar, so the client lives at static/** here — every
# entry name below is relative to that flattened root, not to WEB-INF/classes/.
# The Tailwind directive list is a denylist: revisit it whenever Tailwind gains one. The same
# invariant is asserted for the package stylesheet in packages/tum-aet-ui/check-styles.mjs.
# `jar` prefix-matches entry names, so every stylesheet arrives together with its .css.map, and that
# map embeds the original source, directives and all. Only the .css files are scanned.
ARG ARTEMIS_ROLE
RUN set -eu; \
  war_entries="$(jar tf app/Artemis.jar)"; \
  case "$ARTEMIS_ROLE" in buildagent|aiworker) ! printf '%s\n' "$war_entries" | grep -qx 'static/index.html' || exit 1; exit 0 ;; esac; \
  if ! printf '%s\n' "$war_entries" | grep -qx 'static/index.html'; then \
    echo "app/Artemis.jar does not contain static/index.html. The Angular client is missing from the deployable WAR."; \
    exit 1; \
  fi; \
  if ! printf '%s\n' "$war_entries" | grep -Eq '^static/main-.*\.js$'; then \
    echo "app/Artemis.jar does not contain a static/main-*.js bundle. The Angular client build is incomplete."; \
    exit 1; \
  fi; \
  stylesheets="$(printf '%s\n' "$war_entries" | grep -E '^static/[^/]+\.css$' || true)"; \
  if [ -z "$stylesheets" ]; then \
    echo "app/Artemis.jar contains no static/*.css bundle. The Angular client build is incomplete."; \
    exit 1; \
  fi; \
  extracted="$(mktemp -d)"; \
  printf '%s\n' "$stylesheets" | while read -r stylesheet; do jar xf app/Artemis.jar -C "$extracted" "$stylesheet"; done; \
  if [ "$(find "$extracted" -name '*.css' | wc -l)" -ne "$(printf '%s\n' "$stylesheets" | wc -l)" ]; then \
    echo "Could not extract the stylesheet bundles from app/Artemis.jar; the Tailwind check did not run."; \
    rm -rf "$extracted"; \
    exit 1; \
  fi; \
  uncompiled="$(find "$extracted" -name '*.css' -exec grep -hoE '@(tailwind|source|theme|plugin|custom-variant|utility|variant|apply|reference|config)[[:space:]("{]' {} + | sed 's/.$//' | sort -u | paste -sd' ' - || true)"; \
  rm -rf "$extracted"; \
  if [ -n "$uncompiled" ]; then \
    echo "app/Artemis.jar ships a client stylesheet that still contains Tailwind source directives: ${uncompiled}."; \
    echo "The Tailwind PostCSS plugin did not transform it, so no utility class in the templates resolves to a rule and the application renders unstyled."; \
    echo "Check that a PostCSS config at the repository root names the plugin and that the build which produced this WAR could read it; for an in-Docker build that means .dockerignore."; \
    exit 1; \
  fi

# java expands this argument file just like command-line flags. _JAVA_OPTIONS from
# the deployment can still override the heap for a particular installation.
RUN case "$ARTEMIS_ROLE" in \
      universal|core) printf '%s\n' -Xms2560m -Xmx5120m ;; \
      buildagent) printf '%s\n' -Xms128m -Xmx1024m ;; \
      aiworker) printf '%s\n' -Xms256m -Xmx2048m ;; \
      *) exit 1 ;; \
    esac > /opt/artemis/heap.options

# Use exec form so Java receives shutdown signals directly.
CMD [ "java", \
"-Djdk.tls.ephemeralDHKeySize=2048", \
"-DLC_CTYPE=UTF-8", \
"-Dfile.encoding=UTF-8", \
"-Dsun.jnu.encoding=UTF-8", \
"-Djava.security.egd=file:/dev/./urandom", \
"@/opt/artemis/heap.options", \
"--add-modules", "java.se", \
"--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED", \
"--add-exports", "java.naming/com.sun.jndi.ldap=ALL-UNNAMED", \
"--add-opens", "java.base/java.lang=ALL-UNNAMED", \
"--add-opens", "java.base/java.nio=ALL-UNNAMED", \
"--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", \
"--add-opens", "java.management/sun.management=ALL-UNNAMED", \
"--add-opens", "jdk.management/com.sun.management.internal=ALL-UNNAMED", \
"-jar", "/opt/artemis/app/Artemis.jar" ]

# The AI Worker reports its capacity to core and has no listening HTTP socket.
FROM runtime_base AS runtime_aiworker
ENV SPRING_PROFILES_ACTIVE=prod,aiworker

FROM runtime_base AS runtime_web
EXPOSE 8080
HEALTHCHECK --interval=1s --timeout=5s --start-period=600s --retries=3 \
  CMD wget -q -t1 -T5 -O - http://localhost:8080/management/health/readiness | grep -xq '{"status":"UP"}' || exit 1

FROM runtime_web AS runtime_buildagent
ENV SPRING_PROFILES_ACTIVE=prod,buildagent
VOLUME ["/opt/artemis/data"]

FROM runtime_web AS runtime_universal
EXPOSE 5701 7921
VOLUME ["/opt/artemis/config", "/opt/artemis/data", "/opt/artemis/public/content"]

FROM runtime_universal AS runtime_core

# hadolint ignore=DL3006
FROM ${RUNTIME_STAGE} AS runtime
