FROM openjdk:17-jdk-slim-bullseye as builder

#ARG PROFILE=prod

RUN echo "Installing needed build dependencies" \
  && apt-get update && apt-get install -y curl \
  && curl -fsSL https://deb.nodesource.com/setup_16.x | bash - \
  && apt-get install -y nodejs \
  && npm install --location=global npm@latest

WORKDIR /opt/artemis
# TODO: make this more secure/performant and don't copy everything? or let this be handled just by the .dockerignore?
# maybe build dependencies before that could be more performant than copying everything at once
# COPY src gradle gradlew  .
COPY . .
# TODO: would be obsolete after integration of PR5622
ENV NODE_OPTIONS "--max_old_space_size=6144"
# TODO: recheck why building -Pdev fails
RUN ./gradlew -i --stacktrace --no-daemon -Pprod -Pwar clean bootWar



FROM openjdk:17-jdk-slim-bullseye

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

# Create directories for volumes, create artemis user and set right owners
# also create an empty application-local.yml file as placeholder to be overwritten with a bind volume mount
RUN mkdir -p /opt/artemis/config /opt/artemis/data /opt/artemis/public/content \
  && useradd -Ums /bin/bash artemis \
  && chown -R artemis:artemis /opt/artemis
USER artemis:artemis

# Prepare Entrypoint boostrap.sh
COPY --chown=artemis:artemis src/main/docker/artemis/run_artemis.sh /run_artemis.sh
RUN chmod 774 /run_artemis.sh

# Copy Artemis.war to execution location
WORKDIR /opt/artemis
ARG WAR_PATH=build/libs

COPY --chown=artemis:artemis --from=builder /opt/artemis/$WAR_PATH/*.war Artemis.war

VOLUME ["/opt/artemis/config"]
VOLUME ["/opt/artemis/data"]
VOLUME ["/opt/artemis/public/content"]

EXPOSE 8080

CMD ["/bin/bash", "/run_artemis.sh"]
