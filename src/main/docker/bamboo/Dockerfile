FROM atlassian/bamboo-server:6.10.3

USER root
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update
RUN apt-get install -y maven
RUN apt-get install -y software-properties-common apt-utils
RUN add-apt-repository -y ppa:linuxuprising/java
RUN echo debconf shared/accepted-oracle-license-v1-2 select true | debconf-set-selections && echo debconf shared/accepted-oracle-license-v1-2 seen true | debconf-set-selections
RUN apt-get install -y oracle-java14-installer
RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java

USER ${BAMBOO_USER}
