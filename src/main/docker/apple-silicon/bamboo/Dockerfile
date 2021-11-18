FROM adoptopenjdk:8-jdk-hotspot-focal
LABEL maintainer="Atlassian Bamboo Team" \
      description="Official Bamboo Server Docker Image"

ENV BAMBOO_USER bamboo
ENV BAMBOO_GROUP bamboo

ENV BAMBOO_USER_HOME /home/${BAMBOO_USER}
ENV BAMBOO_HOME /var/atlassian/application-data/bamboo
ENV BAMBOO_INSTALL_DIR /opt/atlassian/bamboo

# Expose HTTP and AGENT JMS ports
ENV BAMBOO_JMS_CONNECTION_PORT=54663
EXPOSE 8085
EXPOSE $BAMBOO_JMS_CONNECTION_PORT

RUN set -x && \
     addgroup ${BAMBOO_GROUP} && \
     adduser ${BAMBOO_USER} --home ${BAMBOO_USER_HOME} --ingroup ${BAMBOO_GROUP} --disabled-password

RUN set -x && \
     apt-get update && \
     apt-get install -y --no-install-recommends software-properties-common && \
     add-apt-repository ppa:git-core/ppa && \
     apt-get install -y --no-install-recommends \
          curl \
          git \
          bash \
          procps \
          openssl \
          openssh-client \
          libtcnative-1 \
          maven \
          tini \
     && \
# create symlink to maven to automate capability detection
     ln -s /usr/share/maven /usr/share/maven3 && \
# create symlink for java home backward compatibility
     mkdir -m 755 -p /usr/lib/jvm && \
     ln -s "${JAVA_HOME}" /usr/lib/jvm/java-8-openjdk-arm64 && \
     rm -rf /var/lib/apt/lists/*

ARG BAMBOO_VERSION=7.2.5
ARG DOWNLOAD_URL=https://www.atlassian.com/software/bamboo/downloads/binary/atlassian-bamboo-${BAMBOO_VERSION}.tar.gz

RUN set -x && \
     mkdir -p ${BAMBOO_INSTALL_DIR}/lib/native && \
     mkdir -p ${BAMBOO_HOME} && \
     ln --symbolic "/usr/lib/x86_64-linux-gnu/libtcnative-1.so" "${BAMBOO_INSTALL_DIR}/lib/native/libtcnative-1.so" && \
     curl --silent -L ${DOWNLOAD_URL} | tar -xz --strip-components=1 -C "$BAMBOO_INSTALL_DIR" && \
     echo "bamboo.home=${BAMBOO_HOME}" > $BAMBOO_INSTALL_DIR/atlassian-bamboo/WEB-INF/classes/bamboo-init.properties && \
     chown -R "${BAMBOO_USER}:${BAMBOO_GROUP}" "${BAMBOO_INSTALL_DIR}" && \
     chown -R "${BAMBOO_USER}:${BAMBOO_GROUP}" "${BAMBOO_HOME}"

VOLUME ["${BAMBOO_HOME}"]
WORKDIR $BAMBOO_HOME

RUN git clone --branch 7.2.5 https://bitbucket.org/atlassian-docker/docker-bamboo-server.git ./git-repo
RUN cp ./git-repo/entrypoint.sh /entrypoint.sh
RUN chown ${BAMBOO_USER}:${BAMBOO_GROUP} /entrypoint.sh

USER ${BAMBOO_USER}

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["/entrypoint.sh"]


LABEL description="Bamboo pre-configured for Artemis"

USER root
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common=0.9* \
    apt-utils=2.0.* \
    maven=3.6.* \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
RUN add-apt-repository -y ppa:linuxuprising/java
RUN echo debconf shared/accepted-oracle-license-v1-3 select true | debconf-set-selections && echo debconf shared/accepted-oracle-license-v1-3 seen true | debconf-set-selections
RUN apt-get install -y --no-install-recommends oracle-java17-installer=17.*
RUN update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-8-openjdk-arm64/bin/java 1
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-arm64/bin/java
# Add file in /artemis/bin/mvn that uses the correct java version and passes all arguments to mvn
RUN mkdir /artemis && mkdir /artemis/bin && printf '#!/bin/bash\nJAVA_HOME=/usr/lib/jvm/java-17-oracle /usr/bin/mvn "$@"\n' > /artemis/bin/mvn && chmod 777 /artemis/bin/mvn


USER ${BAMBOO_USER}
