# Build from the repository root. The base and the canonical harness pin the JDK, wrapper and dependencies.
ARG JAVA_IMAGE=eclipse-temurin:25.0.3_9-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5
FROM ${JAVA_IMAGE}

COPY src/main/resources/templates/java/test/gradle/projectTemplate/ /opt/cache-fixture/
COPY src/main/resources/templates/hyperion/readiness/java/solution/ /opt/cache-fixture/assignment/
COPY src/main/resources/templates/hyperion/readiness/java/tests/behavior/ /opt/cache-fixture/test/
COPY src/main/resources/templates/hyperion/readiness/java/tests/structural/ /opt/cache-fixture/test/
WORKDIR /opt/cache-fixture
COPY src/main/resources/templates/hyperion/readiness/java/tests/SecurityPolicy.yaml /opt/cache-fixture/SecurityPolicy.yaml

# Render the supported non-SCA, non-sequential, public-repository template, just as exercise creation does.
# Keep Teamscale and all canonical dependency versions: an offline cache is not a different grading harness.
RUN sed -i \
        -e '/\/\/ %static-code-analysis-start%/,/\/\/ %static-code-analysis-stop%/d' \
        -e '/\/\/ %sequential-start%/,/\/\/ %sequential-stop%/d' \
        -e '/\/\/ %maven-central-mirror-start%/,/\/\/ %maven-central-mirror-stop%/d' \
        -e 's/${studentWorkingDirectoryNoSlash}/assignment\/src/g' \
        -e 's/${exerciseNamePomXml}/hyperion-readiness/g' build.gradle settings.gradle \
    && chmod +x gradlew \
    && ./gradlew --no-daemon clean compileJava compileTestJava test \
    && ./gradlew --no-daemon --offline clean compileJava compileTestJava test \
    && rm -rf /opt/cache-fixture /root/.gradle/daemon /root/.gradle/notifications \
    && chmod o+x /root && chmod -R a+rX /root/.gradle

# The supervisor overrides the entrypoint and places every writable path on bounded tmpfs.
# Verification copies the public offline cache into /tmp before running the captured, immutable harness.
WORKDIR /workspace
USER 1000:1000
