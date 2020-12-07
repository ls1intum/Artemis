FROM atlassian/bamboo-server:7.1.2

LABEL description="Bamboo pre-configured for Artemis"

USER root
ENV DEBIAN_FRONTEND noninteractive
# adapted from norionomura/swift
ENV SWIFT_BRANCH=swift-5.3.1-release \
    SWIFT_PLATFORM=ubuntu18.04 \
    SWIFT_VERSION=5.3.1-RELEASE

RUN apt-get update
RUN apt-get install -y maven
RUN apt-get install -y software-properties-common apt-utils
RUN add-apt-repository -y ppa:linuxuprising/java
RUN echo debconf shared/accepted-oracle-license-v1-2 select true | debconf-set-selections && echo debconf shared/accepted-oracle-license-v1-2 seen true | debconf-set-selections
RUN apt-get install -y oracle-java15-installer
RUN update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java 1
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
# Add file in /artemis/bin/mvn that uses the correct java version and passes all arguments to mvn
RUN mkdir /artemis && mkdir /artemis/bin && printf '#!/bin/bash\nJAVA_HOME=/usr/lib/jvm/java-15-oracle /usr/bin/mvn "$@"\n' > /artemis/bin/mvn && chmod 777 /artemis/bin/mvn

# adapted from norionomura/swift and norionomura/swiftlint
RUN apt-get install -y gnupg2
# Install Swift keys
RUN curl https://swift.org/keys/all-keys.asc | gpg2 --import -
# Install Swift toolchain
# -> https://swift.org/builds/swift-5.3.1-release/ubuntu1804/swift-5.3.1-RELEASE/swift-5.3.1-RELEASE-ubuntu18.04.tar.gz
RUN SWIFT_ARCHIVE_NAME=swift-$SWIFT_VERSION-$SWIFT_PLATFORM && \
    SWIFT_URL=https://swift.org/builds/$SWIFT_BRANCH/$(echo "$SWIFT_PLATFORM" | tr -d .)/swift-$SWIFT_VERSION/$SWIFT_ARCHIVE_NAME.tar.gz && \
    curl -O $SWIFT_URL && \
    curl -O $SWIFT_URL.sig && \
    gpg2 --verify $SWIFT_ARCHIVE_NAME.tar.gz.sig && \
    tar -xvzf $SWIFT_ARCHIVE_NAME.tar.gz --directory / --strip-components=1 && \
    rm -rf $SWIFT_ARCHIVE_NAME* /tmp/* /var/tmp/* && \
    chmod -R o+r /usr/lib/swift

# install SwiftLint
RUN apt-get install -y libcurl4-openssl-dev libxml2-dev && rm -r /var/lib/apt/lists/*
RUN git clone --branch master https://github.com/realm/SwiftLint.git && \
    cd SwiftLint && \
    swift build --configuration release -Xswiftc -static-stdlib && \
    mv `swift build --configuration release -Xswiftc -static-stdlib --show-bin-path`/swiftlint /usr/bin && \
    cd .. && \
    rm -rf SwiftLint

USER ${BAMBOO_USER}
