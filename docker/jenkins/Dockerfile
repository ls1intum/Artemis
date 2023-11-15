FROM docker.io/jenkins/jenkins:lts

LABEL description="Jenkins with plugins pre-installed for Artemis"

USER root

# Install third-party plugins required by Artemis
COPY plugins.yml /usr/share/jenkins/ref/plugins.yml
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.yml

# Disables the first-time setup wizard of Jenkins
# (Uncomment this line if you're using jenknis-casc-config.yml for auto-configuration)
# ENV JAVA_OPTS -Djenkins.install.runSetupWizard=false

# setup docker
RUN apt-get update -y \
    && apt-get install -qqy apt-transport-https ca-certificates curl gnupg2 software-properties-common
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository \
   "deb [arch=$(dpkg --print-architecture)] https://download.docker.com/linux/debian \
   $(lsb_release -cs) \
   stable"
RUN apt-get update -y \
    && apt-get -y install docker-ce && rm -rf /var/apt/cache/*
RUN usermod -aG docker jenkins

USER jenkins
