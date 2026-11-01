# Build from the repository root. The base and the canonical harnesses pin the JDK, wrapper, Maven and dependencies.
# One image serves both Java build tools: Gradle (PLAIN_GRADLE, GRADLE_GRADLE) and Maven (PLAIN_MAVEN, MAVEN_MAVEN).
ARG JAVA_IMAGE=eclipse-temurin:25.0.4.1_1-jdk@sha256:3ab7fc8d0b4ade8fef96903fb36767c1dbb4fd1535dcfad85b59c17afbbb5857
FROM ${JAVA_IMAGE}

ARG MAVEN_SHA512=831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6

# Pinned Apache Maven 3.9.16 distribution: ADD verifies the SHA-256, the RUN verifies Apache's published SHA-512.
ADD --checksum=sha256:80ffca22aed9e8b9713a232f3394fd81d7f20322df75efdb2b047dbd3e3a23bb \
    https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz /tmp/maven.tar.gz
RUN echo "${MAVEN_SHA512}  /tmp/maven.tar.gz" | sha512sum -c - \
    && mkdir -p /opt/maven \
    && tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1 \
    && rm /tmp/maven.tar.gz \
    && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn

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

# Both Maven test harnesses resolve different dependency versions (MAVEN_MAVEN pins its own commons-lang3), so each is warmed.
COPY src/main/resources/templates/java/test/maven/projectTemplate/pom.xml /opt/maven-cache-fixture/plain_maven.xml
COPY src/main/resources/templates/java/maven_maven/test/projectTemplate/pom.xml /opt/maven-cache-fixture/maven_maven.xml
COPY src/main/resources/templates/hyperion/readiness/java/solution/ /opt/maven-cache-fixture/assignment/
COPY src/main/resources/templates/hyperion/readiness/java/tests/behavior/ /opt/maven-cache-fixture/test/
COPY src/main/resources/templates/hyperion/readiness/java/tests/structural/ /opt/maven-cache-fixture/test/
COPY src/main/resources/templates/hyperion/readiness/java/tests/SecurityPolicy.yaml /opt/maven-cache-fixture/SecurityPolicy.yaml
WORKDIR /opt/maven-cache-fixture

# Render the canonical Maven test harnesses the same way, then run the canonical LocalCI phases online and offline.
# At runtime the repository is a read-only tail behind a private one; Maven ignores a tail artifact's origin, so an exercise
# pom that declares a differently named repository (e.g. a Maven Central mirror) still resolves the same pinned artifacts.
RUN sed -i \
        -e '/<!--%static-code-analysis-start%-->/,/<!--%static-code-analysis-stop%-->/d' \
        -e '/<!--%maven-central-mirror-start%-->/,/<!--%maven-central-mirror-stop%-->/d' \
        -e 's#${studentWorkingDirectory}#/assignment/src#g' \
        -e 's#${exerciseNamePomXml}#hyperion-readiness#g' \
        -e 's#${exerciseName}#hyperion-readiness#g' \
        -e 's#${packageName}#de.tum.cit.aet.reference#g' \
        -e 's#${packaging}#jar#g' plain_maven.xml maven_maven.xml \
    && sed -i 's/JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ/JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ/' SecurityPolicy.yaml \
    && for pom in plain_maven.xml maven_maven.xml; do \
        cp "$pom" pom.xml \
        && mvn -B -Dmaven.repo.local=/root/.m2/repository clean compile \
        && mvn -B -Dmaven.repo.local=/root/.m2/repository test \
        && mvn -B -o -Dmaven.repo.local=/root/.m2/repository clean compile \
        && mvn -B -o -Dmaven.repo.local=/root/.m2/repository test \
        || exit 1; \
    done \
    && rm -rf /opt/maven-cache-fixture \
    && chmod -R a+rX /root/.m2

# The supervisor overrides the entrypoint and places every writable path on bounded tmpfs.
# Gradle verification copies the public offline cache into /tmp before running the captured, immutable harness;
# Maven verification reads /root/.m2/repository as a read-only tail and writes only to a private /tmp repository.
WORKDIR /workspace
USER 1000:1000
