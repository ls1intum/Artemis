FROM atlassian/bamboo-server:7.2.5

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
RUN echo debconf shared/accepted-oracle-license-v1-2 select true | debconf-set-selections && echo debconf shared/accepted-oracle-license-v1-2 seen true | debconf-set-selections
RUN apt-get install -y --no-install-recommends oracle-java16-installer=16.*
RUN update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java 1
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
# Add file in /artemis/bin/mvn that uses the correct java version and passes all arguments to mvn
RUN mkdir /artemis && mkdir /artemis/bin && printf '#!/bin/bash\nJAVA_HOME=/usr/lib/jvm/java-16-oracle /usr/bin/mvn "$@"\n' > /artemis/bin/mvn && chmod 777 /artemis/bin/mvn


USER ${BAMBOO_USER}
