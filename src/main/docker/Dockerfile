FROM openjdk:17-jdk-slim-bullseye

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JHIPSTER_SLEEP=0 \
    JAVA_OPTS=""

# Specify profiles
ENV PROFILES ""

# See https://github.com/ls1intum/Artemis/issues/4439
RUN echo "Fixing locales" \
  && apt-get update && apt-get install -y --no-install-recommends locales \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen

ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

ARG GOSU_VERSION=1.12

RUN echo "Installing prerequisites" \
  && apt-get update && apt-get install -y --no-install-recommends \
  graphviz \
  wget \
  && dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
  && wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" \
  && chmod +x /usr/local/bin/gosu \
  && gosu nobody true

# Copy default configuration to container
WORKDIR /defaults/Artemis
COPY src/main/resources/config/application*.yml ./

# Copy Artemis.war to execution location
WORKDIR /opt/Artemis
ARG WAR_PATH=build/libs
COPY $WAR_PATH/*.war Artemis.war

COPY bootstrap.sh /bootstrap.sh

RUN chmod +x /bootstrap.sh \
  && useradd -ms /bin/bash artemis

# Create directories for volumes
RUN mkdir -p /opt/Artemis/config /opt/Artemis/data /opt/Artemis/public/content

VOLUME ["/opt/Artemis/config"]
VOLUME ["/opt/Artemis/data"]
VOLUME ["/opt/Artemis/public/content"]

EXPOSE 8080

CMD exec /bootstrap.sh
