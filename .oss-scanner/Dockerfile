#-----------------------------------------------------------------------------------------------------------------------
# OSS Scanner build (https://github.com/anthropics/oss-scanner, projects/artemis)
#-----------------------------------------------------------------------------------------------------------------------
# This is not the production image; that is deployment/docker/artemis/Dockerfile. The scanner builds this file with the
# repository as the build context, then analyses the result with no network. Everything the scanner needs offline
# (Gradle dependencies, Node, pnpm packages, compiled classes, the WAR) is therefore fetched here, and it all stays in the
# image: no BuildKit cache mounts, because those are not part of the final image.
#
# The scanner gives the build 45 minutes. Keep the steps below cheap enough to stay well inside that.
#
# To try it, run `tools/check artemis` in a checkout of the oss-scanner repository.
#-----------------------------------------------------------------------------------------------------------------------

# Same pinned JDK as the production Dockerfile; Renovate bumps both.
FROM docker.io/library/eclipse-temurin:25.0.4.1_1-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc

# scanner contract: the checkout lives inside the image, at /src
# .oss-scanner/Dockerfile.dockerignore keeps .github in the context, unlike the root .dockerignore.
COPY . /src
WORKDIR /src

# Node and pnpm are the exact versions pinned in gradle.properties: the Gradle node plugin downloads them into
# /src/.gradle, then installs the client dependencies into /src/node_modules.
RUN ./gradlew --no-daemon --stacktrace -Pprod -Pwar pnpmInstall

# Build the server and the Angular client the way the production image does, minus the SBOM. This leaves the compiled
# classes in /src/build/classes and the deployable WAR in /src/build/libs/Artemis-<version>.war.
RUN ./gradlew --no-daemon --stacktrace -Pprod -Pwar bootWar -x pnpmInstall

# Put node and pnpm on the PATH so the client tests run without Gradle: `pnpm exec vitest run <spec>`.
RUN set -eu; \
    node_bin="$(find /src/.gradle/nodejs -maxdepth 3 -type f -name node | head -n 1)"; \
    pnpm_bin="$(find /src/.gradle/pnpm -maxdepth 3 -path '*/bin/pnpm' | head -n 1)"; \
    if [ -z "$node_bin" ] || [ -z "$pnpm_bin" ]; then \
      echo "Could not find the Gradle-installed node ('$node_bin') or pnpm ('$pnpm_bin') under /src/.gradle"; \
      exit 1; \
    fi; \
    ln -s "$node_bin" /usr/local/bin/node; \
    ln -s "$pnpm_bin" /usr/local/bin/pnpm; \
    node --version; \
    test "$(pnpm --version)" = "$(sed -n 's/^pnpm_version=//p' gradle.properties)"

# Server tests use PostgreSQL through Testcontainers by default, which needs Docker and cannot work offline.
# -Dzonky.test.database.type=H2 runs them against in-process H2 instead. Run one cheap integration test: it fetches the
# test dependencies, compiles the tests and starts the Spring context, so everything the offline server tests need is in
# the image. The step is strict on purpose. If this test cannot pass, the scanner would get an image that cannot run
# server tests, and a warning in the build log is easy to miss.
RUN ./gradlew --no-daemon --stacktrace test --tests ClientForwardTest -x webapp -Dzonky.test.database.type=H2
