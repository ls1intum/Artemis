FROM eclipse-temurin:17 as builder

ENV NODE_OPTIONS="--max-old-space-size=6144"

COPY . /artemis-build
WORKDIR /artemis-build
RUN ./gradlew -Pprod -Pwar bootWar

# ---

FROM eclipse-temurin:17

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

# Specify profiles for Spring Boot
ENV spring.profiles.active ""

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

ARG GOSU_VERSION=1.12

RUN echo "Installing gosu (needed for bootstrap.sh)" \
  && dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
  && wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" \
  && chmod +x /usr/local/bin/gosu \
  && gosu nobody true

# Copy default configuration to container
WORKDIR /defaults/artemis
COPY src/main/resources/config/application*.yml ./

# Copy Artemis.war to execution location
WORKDIR /opt/artemis
ARG WAR_PATH=/artemis-build/build/libs
COPY --from=builder $WAR_PATH/*.war Artemis.war

COPY bootstrap.sh /bootstrap.sh

RUN chmod +x /bootstrap.sh \
  && useradd -ms /bin/bash artemis

# Create directories for volumes
RUN mkdir -p /opt/artemis/config /opt/artemis/data /opt/artemis/public/content

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080

ENTRYPOINT ["/bin/bash", "/bootstrap.sh"]
