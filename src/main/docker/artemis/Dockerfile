#-----------------------------------------------------------------------------------------------------------------------
# build stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:17-jdk as builder

WORKDIR /opt/artemis
# so far just using the .dockerignore to define what isn't necessary here
COPY . .

RUN ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar clean bootWar

#-----------------------------------------------------------------------------------------------------------------------
# runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM docker.io/library/eclipse-temurin:17-jdk as runtime

# Docker Compose: wget (healthcheck docker compose)
# Artemis: graphviz, locales
RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends locales graphviz wget \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# See https://github.com/ls1intum/Artemis/issues/4439
RUN echo "Fixing locales" \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen
ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

# Create directories for volumes, create artemis user and set right owners
RUN mkdir -p /opt/artemis/config /opt/artemis/data /opt/artemis/public/content \
  && useradd -Ums /bin/bash artemis \
  && chown -R artemis:artemis /opt/artemis
USER artemis:artemis

# Copy Artemis.war to execution location
WORKDIR /opt/artemis
ARG WAR_PATH=build/libs

COPY --chown=artemis:artemis --from=builder /opt/artemis/$WAR_PATH/*.war Artemis.war

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080

CMD java \
-Djdk.tls.ephemeralDHKeySize=2048 \
-DLC_CTYPE=UTF-8 \
-Dfile.encoding=UTF-8 \
-Dsun.jnu.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom \
-Xmx5120m \
-Xms2560m \
--add-modules java.se \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
--add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.management/sun.management=ALL-UNNAMED \
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
-jar /opt/artemis/Artemis.war
