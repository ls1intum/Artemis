FROM jenkins/jenkins:lts

LABEL description="Jenkins with maven pre-installed for Artemis"

USER root

RUN apt update
RUN apt-get install -y maven
RUN cd /usr/lib/jvm && \
    wget https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz && \
    tar -zxf OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz \
    && mv jdk-12.0.2+10 java-12-openjdk-amd64 \
    && rm OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz
RUN chown -R root:root /usr/lib/jvm/java-12-openjdk-amd64
RUN JAVA_HOME="/usr/lib/jvm/java-12-openjdk-amd64" && export JAVA_HOME
ENV JAVA_HOME /usr/lib/jvm/java-12-openjdk-amd64

USER jenkins
