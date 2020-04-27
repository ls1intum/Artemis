FROM atlassian/bamboo-server:7.0.2

LABEL description="Bamboo pre-configured for Artemis"

USER root
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update
RUN apt-get install -y maven
RUN apt-get install -y software-properties-common apt-utils
RUN add-apt-repository -y ppa:linuxuprising/java
RUN echo debconf shared/accepted-oracle-license-v1-2 select true | debconf-set-selections && echo debconf shared/accepted-oracle-license-v1-2 seen true | debconf-set-selections
RUN apt-get install -y oracle-java14-installer
RUN update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java 1
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
RUN apt-get install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
RUN add-apt-repository -y "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
RUN apt-get install -y docker-ce docker-ce-cli containerd.io sudo

# REPLACE 1001 with the return value of `ls -lan /var/run/docker.sock | awk 'NR==1 {print $4}'` on the host
RUN groupmod -g 1001 docker && usermod -aG docker ${BAMBOO_USER} && usermod -aG 0 ${BAMBOO_USER}

USER ${BAMBOO_USER}
